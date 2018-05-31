#include <jni.h>
#include <string>
#include <stdlib.h>
#include <dlfcn.h>
#include <android/log.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/epoll.h>
#include <string.h>

#include <fstream>
#include <algorithm>
#include <deque>
#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/tracking/tracker.hpp>

#define TS_ROI_DATA_FILE "/sys/touchscreen/roi_data_internal"
#define TS_DIFF_FILE  "/data/diff_data"
#define TS_READ 0
#define TS_WRITE 1
#define  TAG "READ_DIFF"

#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define DIFF_LENGTH  32*18
#define MAXLINE 2048

#define GRID_RES_X 18
#define GRID_RES_Y 32
#define KCF_REFRESH_INTERVAL 25
#define CHECK_SUM 50
#define DIRTY_SUM 32300

#define FROMFILE
#define REALTIME
//#define FROMDEV
//#define RECORD

using namespace cv;
using namespace std;

struct Frame
{
    int capacity[32][18];
    int frameID;
};

struct FlaseTouchData{
    int x;
    int y;
    int id;
};

struct FalseFrameData{
    int16_t diffData[32*18];
    uint16_t rawData[32*18];
    int touchNum;
    int downNum;
    struct FlaseTouchData touchData[10];
};

/////////////////////////////////////////////////for calcPoint()////////////////////////////////
deque<Point> points_buffer;

deque<Frame> frames;
pthread_mutex_t frames_mutex;
int frameID = 0;
bool last_dirty = false;

int lastsum = 31000;
int lastPatternSum = 0;

const int lenx = GRID_RES_X * 5, leny = GRID_RES_Y * 5;
const float screenx = 1440, screeny = 2560;
const float mulx = screenx / lenx, muly = screeny / leny;

const int MIN_PATTERN_SIZE = 10;
Mat pattern, lanc(leny, lenx, CV_32F), Image[2];

Point last_result(lenx / 2, leny / 2), last_point[2];
Ptr<TrackerKCF> tracker[2];

Point firstPoint = Point(0, 0);
pthread_t update_threads[2];
const int MIN_X = 0, MAX_X = 1339, MIN_Y = 0, MAX_Y = 2559;
int last = 0, now = 1, frame_count = 0;
int tracker_last = 1, tracker_now = 0;
Rect2d box, box_last;
bool succ, succ_last;

const int PRESS_THRESHOLD = 2000;

int touchSum;
int checked = 0;
bool spinFlag = false, swipeFlag = false;
int pressFlag = 0;
Rect checkSpinPattern;
RotatedRect checkSpinFirstTouch;
bool checkSpinRectFlag;
int checkSpinSample = 0;
int last_angle = -1, total_angle = 0, clkwise = 0, anticlkwise = 0;

const int MAX_PRESS_DIST = 240;
const int MAX_SWIPE_DIST = 240;
double press_dist;
//////////////////////////////////////////////////////////////////////////////////////////////////////

jmethodID callBack_method, notify_method;
pthread_t thread_1, thread_2;
JavaVM* g_jvm;
jobject obj;
int pipefd[2] = {-1,-1};
string filename = "";

void *run(void *args){

    JNIEnv *env;
    int timeoutMs = 120;//120ms

    int status  = g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if(status < 0){
        status = g_jvm->AttachCurrentThread(&env, NULL);
        if(status < 0){
            env = NULL;
            LOGD("get Env Failed..");
            return NULL;
        }
    }

    bool touching = false;

    jshortArray diffdata = env->NewShortArray(DIFF_LENGTH*2+1+8);//diff + raw + num_touches + 4 (x, y) for each touch
    jshortArray rawdata = env->NewShortArray(DIFF_LENGTH);
    //jintArray touchNumdata = env->NewIntArray(1);
    int touchdata[1] = {0};
    //jshort * data_ = env->GetShortArrayElements(data,NULL);
    short buf[MAXLINE] = {0};
    LOGD("Thread Coming...");
    //env->SetIntArrayRegion(data,0,DIFF_LENGTH,data_);

    int epfd,nfds,fd,recvNum,sockfd;
    struct epoll_event ev,events[20];
    struct timeval tv;
    epfd = epoll_create(10);
    fd  = open(TS_DIFF_FILE,O_RDONLY | O_NONBLOCK);
    if(fd < 0){
        LOGE("open diff_data Error");
        return NULL;
    }
    ev.data.fd=fd;
    ev.events = EPOLLIN;
    epoll_ctl(epfd,EPOLL_CTL_ADD,fd,&ev);

    if(pipe(pipefd) < 0){
        LOGE("pipe Error");
        close(fd);
        return NULL;
    }
    ev.data.fd = pipefd[0];
    ev.events = EPOLLIN;
    epoll_ctl(epfd,EPOLL_CTL_ADD,pipefd[0],&ev);
    int ret = fcntl(pipefd[0],F_SETFL,(fcntl(pipefd[0],F_GETFL) | O_NONBLOCK));
    if(ret < 0){
        LOGE("fcntl pipefd Error");
        return  NULL;
    }
    while(1)
    {
        nfds=epoll_wait(epfd,events,5,timeoutMs);
        if(nfds == 0){
            continue; //超时
        }

        for(int i=0;i<nfds;++i)
        {
            if(events[i].events & EPOLLIN)//
            {
                if ((sockfd = events[i].data.fd) < 0)
                    continue;
                if(sockfd == fd)  //diffData
                {
                    struct FalseFrameData frameData;
                    memset(&frameData, 0, sizeof(frameData));
                    recvNum = read(sockfd, (char *) &frameData, sizeof(frameData));
                    //memset(buf,0,MAXLINE*sizeof(short));
                    //recvNum = read(sockfd, (char *)buf,MAXLINE);
                    if (recvNum < 0) {
                        LOGE("Read Eroor");
                        break;
                    } else if (recvNum != sizeof(frameData)) {
                        LOGE("frameData  InComplete !!!");
                        break;
                    }
                    gettimeofday(&tv, NULL);
                    //not log the frame now
                    //LOGD("Time:%ld  TouchNum:%d  DownNum:%d", tv.tv_usec / 1000, frameData.touchNum,
                    //     frameData.downNum);
                    /*for (int i = 0; i < frameData.touchNum; i++) {
                        LOGD("ID:%d  x:%d  y:%d", frameData.touchData[i].id,
                             frameData.touchData[i].x, frameData.touchData[i].y);
                    }*/
                    //new add
                    Frame f;
                    int16_t rawdata_after[32 * 18 + 32 * 18 + 1 + 8];
                    int sum = 0;
                    for (int j = 0; j < 32; j++)
                        for (int k = 0; k < 18; k++)
                        {
                            f.capacity[j][k] = rawdata_after[j * 18 + k] = (jshort) (frameData.rawData[j * 18 + k] - 30000);
#ifdef RECORD
                            sum += f.capacity[j][k];
#endif
                        }

#ifdef REALTIME
                    f.frameID = frameID++;
                    pthread_mutex_lock(&frames_mutex);
                    frames.push_back(f);
                    if (frames.size() > 100) {
                        frames.pop_front();
                    }
                    pthread_mutex_unlock(&frames_mutex);
#endif

#ifdef RECORD
                    env->SetShortArrayRegion(diffdata,0,DIFF_LENGTH*2+1+8,rawdata_after);
                    if (sum > 1620000)
                    {
                        if (!touching)
                            touching = true;
                    } else
                    {
                        if (touching)
                        {
                            env->CallVoidMethod((jobject)args,callBack_method,diffdata, true);
                            touching = false;
                        }
                    }
                    env->CallVoidMethod((jobject)args,callBack_method,diffdata, false);
#endif
                    /*
                    for (int i = 32 * 18; i < 32 * 18 * 2; i++) {
                        rawdata_after[i] = frameData.diffData[i] + 32768 + 100;
                    }
                    rawdata_after[32 * 18 * 2] = (jshort) (frameData.touchNum);
                    for (int i = 0; i < frameData.touchNum; i++) {
                        rawdata_after[i*2+32*18*2+1] = frameData.touchData[i].x;
                        rawdata_after[i*2+1+32*18*2+1] = frameData.touchData[i].y;
                    }
                    for(int i = frameData.touchNum;i<4;i++){
                        rawdata_after[i*2+32*18*2+1] = 0;
                        rawdata_after[i*2+1+32*18*2+1] = 0;
                    }
                    //env->SetShortArrayRegion(diffdata,0,DIFF_LENGTH,frameData.diffData);
                    env->SetShortArrayRegion(diffdata,0,DIFF_LENGTH*2+1+8,rawdata_after);
                    //env->SetIntArrayRegion(touchNumdata, 1, 1, touchdata);
                    //env->SetShortArrayRegion(rawdata,0,DIFF_LENGTH,(jshort*)frameData.rawData);//回调java处理函数
                    env->CallVoidMethod((jobject)args,callBack_method,diffdata);
                    */
                }else{  //app exit
                    char buf[10];
                    read(sockfd,buf,10);
                    LOGD("Read Diff exit------------");
                    close(pipefd[0]);
                    close(pipefd[1]);
                    close(fd);
                    env->DeleteGlobalRef((jobject)args);
                    return NULL;
                }
            }
        }
    }
}

template <typename T>
int matSum(Mat& m)
{
    int sum = 0;
    T min = 255, max = 0;
    for (int i = 0; i < m.rows; i++)
        for (int j = 0; j < m.cols; j++)
        {
            T ele = m.at<T>(i, j);
            sum += ele;
            if (ele < min) min = ele;
            if (ele > max) max = ele;
        }
    return sum;
}

bool findPattern(Mat& m, Rect& pattern, RotatedRect& firstTouch)
{
/*
    if (checked < CHECK_SUM)                    //find pattern was done while check spin
    {
        if (checkSpinRectFlag) {
            pattern = checkSpinPattern;
            firstTouch = checkSpinFirstTouch;
        }
        return checkSpinRectFlag;
    }*/
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    findContours(m, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));

    bool rect = false;
    Rect rectsum;
    vector<Point> save;
    for (int i = 0; i < contours.size(); i++) {
        if (contours[i].size() > MIN_PATTERN_SIZE && contours[i].size()) {
            rect = true;
            Rect recti = boundingRect(contours.at(i));
            rectsum = rectsum | recti;
            save.insert(save.end(), contours.at(i).begin(), contours.at(i).end());
        }
    }
    if (rect) {
        pattern = rectsum;
        firstTouch = minAreaRect(save);
    }
    return rect;

}

void calcFirstPoint(RotatedRect& rect, Point& firstPoint) {
    Point pointB;
    Point2f vertices[4];
    rect.points(vertices);
    Point pointA;
    if (rect.size.width < rect.size.height) { //normal
        pointB.x = vertices[2].x;
        pointB.y = vertices[2].y;
        pointA.x = pointB.x - 15;
        pointA.y = pointB.y + 15;
    }
    else {
        pointB.x = vertices[3].x;
        pointB.y = vertices[3].y;
        pointA.x = pointB.x - 15;
        pointA.y = pointB.y - 15;
    }
    float theta = abs(rect.angle) / 180 * 3.1415927;
    double pointA2x = pointB.x - pointA.x;
    double pointA2y = pointA.y - pointB.y;
    double pointC2x = pointA2x*cos(theta) - pointA2y*sin(theta);
    double pointC2y = pointA2y*cos(theta) + pointA2x*sin(theta);
    //double pointCx = pointB.x - pointC2x;
    //double pointCy = pointB.y + pointC2y;
    firstPoint.x = pointB.x - pointC2x;
    firstPoint.y = pointB.y + pointC2y;
}

void calcFirstPoint(Mat& m, RotatedRect& rect, Point& firstPoint)
{
    double min, max;
    Point minp, maxp;
    //Rect r = rect.boundingRect();
    //Mat tmp(m, r);
    minMaxLoc(m, &min, &max, &minp, &maxp);
    firstPoint = maxp;
}

void restrict(Point& p, int lx, int rx, int ly, int ry)
{
    if (p.x < lx) p.x = lx;
    if (p.x > rx) p.x = rx;
    if (p.y < ly) p.y = ly;
    if (p.y > ry) p.y = ry;
}

void trackerInit(Ptr<TrackerKCF>& t, Mat& m, Rect& r)
{
    TrackerKCF::Params para;
    para.desc_pca = TrackerKCF::GRAY;
    para.compressed_size = 1;
    t = TrackerKCF::create(para);
    t->init(m, r);
}

void* update(void* nouse)
{
    succ = tracker[tracker_now]->update(Image[last], box);
    return NULL;
}

void* update_last(void* nouse)
{
    succ_last = tracker[tracker_last]->update(Image[now], box_last);
    return NULL;
}

Point matchPattern(Mat& m, Mat& p)
{
    Mat ImageResult;
    matchTemplate(m, p, ImageResult, CV_TM_SQDIFF);
    double minValue, maxValue;
    Point minPoint, maxPoint;
    minMaxLoc(ImageResult, &minValue, &maxValue, &minPoint, &maxPoint, Mat());
    return minPoint;
}

void cubicSmooth5(deque<Point>& in)
{
    int N = in.size();
    Point out[5];
    if (N >= 5)
    {
        for (int i = N - 5; i <= N - 3; i++)
        {
            if (i == 0) out[5 - N + i] = (69.0 * in[0] + 4.0 * in[1] - 6.0 * in[2] + 4.0 * in[3] - in[4]) / 70.0;
            else if (i == 1) out[5 - N + i] = (2.0 * in[0] + 27.0 * in[1] + 12.0 * in[2] - 8.0 * in[3] + 2.0 * in[4]) / 35.0;
            else out[5 - N + i] = (-3.0 * (in[i - 2] + in[i + 2]) + 12.0 * (in[i - 1] + in[i + 1]) + 17.0 * in[i]) / 35.0;
        }
        out[3] = (2.0 * in[N - 5] - 8.0 * in[N - 4] + 12.0 * in[N - 3] + 27.0 * in[N - 2] + 2.0 * in[N - 1]) / 35.0;
        out[4] = (-in[N - 5] + 4.0 * in[N - 4] - 6.0 * in[N - 3] + 4.0 * in[N - 2] + 69.0 * in[N - 1]) / 70.0;
        for (int i = N - 5; i < N; i++)
            in[i] = out[5 - N + i];
    }
}

bool checkSwipe(int dist)
{
    if (checked > CHECK_SUM) return false;
    if (points_buffer.size() > 1) {
        int dx = points_buffer[points_buffer.size() - 1].x - points_buffer[0].x;
        int dy = points_buffer[points_buffer.size() - 1].y - points_buffer[0].y;
        press_dist = sqrt(dx * dx + dy * dy);
        if (dx * dx + dy * dy > dist * dist)
            return true;
        else return false;
    }
    else return false;
}

void checkSpin(JNIEnv* env, int sum, Mat& binaryImage)
{
    vector<vector<Point>> contours;
    vector<Vec4i> hierarchy;
    findContours(binaryImage, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));
    Rect rectsum;
    vector<Point> save;
    bool rect = false;
    for (int i = 0; i < contours.size(); i++) {
        if (contours[i].size() > MIN_PATTERN_SIZE && contours[i].size()) {
            rect = true;
            Rect recti = boundingRect(contours.at(i));
            rectsum = rectsum | recti;
            save.insert(save.end(), contours.at(i).begin(), contours.at(i).end());
        }
    }

    checkSpinRectFlag = rect;
    if (rect){
        checkSpinPattern = rectsum;
        checkSpinFirstTouch = minAreaRect(save);
    } else return;
    RotatedRect firstTouch = checkSpinFirstTouch;

    //sample angle
    if (checkSpinSample != 2)
    {
        checkSpinSample++;
        return;
    }
    else checkSpinSample = 0;

    //adjust angle from [-90, 0] to [0, 180]
    int angle;
    if (firstTouch.size.width < firstTouch.size.height) {
        angle = firstTouch.angle + 90;
    }
    else angle = firstTouch.angle;
    if (angle < 0) angle += 180;
    //

    int spinInterval = (spinFlag ? 20 : 30);

    if (last_angle != -1)
    {
        int diff = angle - last_angle;
        if (abs(diff) > 90)
        {
            if (last_angle < 90) diff -= 180;
            else diff += 180;
        }

        if (diff > 20) return;    //invalid

        int tmp = total_angle;
        total_angle += diff;

        if (spinFlag)
        {
            if ((tmp > 0) == (total_angle > 0))
            {
                int in = total_angle / spinInterval - tmp / spinInterval;
                if (in > 0) anticlkwise++, clkwise = 0;
                if (in < 0) clkwise++, anticlkwise = 0;
            }
            else
            {
                if (total_angle > 0) anticlkwise++, clkwise = 0;
                else clkwise++, anticlkwise = 0;
            }
        }
        else
        {
            if (abs(total_angle) > spinInterval)
            {
                spinFlag = true;
                if (total_angle > 0) env->CallVoidMethod(obj, callBack_method, 0, -1, true);
                else env->CallVoidMethod(obj, callBack_method, -1, 0, true);
            }
        }
    }

    if (anticlkwise == 3)
    {
        env->CallVoidMethod(obj, callBack_method, 0, -1, true);
        anticlkwise = 0;
    }

    if (clkwise == 3)
    {
        env->CallVoidMethod(obj, callBack_method, -1, 0, true);
        clkwise = 0;
    }

    last_angle = angle;
}

void sendPoint(JNIEnv* env, bool touchEnd, Point result = Point())
{
    if (touchEnd)
    {
        //for points_buffer
        int N = points_buffer.size();
        if (N > 0 && N < 5) {
            for (int i = 0; i < N; i++)
            {
                Point p = points_buffer[i];
                p.x = (float) (p.x - MIN_X) / (MAX_X - MIN_X) * screenx;
                p.y = (float) (p.y - MIN_Y) / (MAX_Y - MIN_Y) * screeny;
                env->CallVoidMethod(obj, callBack_method, p.x, p.y, true);
            }
        }

        env->CallVoidMethod(obj,callBack_method, 0, last_angle, false);

        checked = 0;
        points_buffer.clear();
    }
    else
    {
        checked++;
        points_buffer.push_back(result);

        //for points_buffer
        int N = points_buffer.size();
        if (N >= 5)
        {
            cubicSmooth5(points_buffer);

            if (N == 5)
            {
                Point p = points_buffer[0];
                p.x = (float)(p.x - MIN_X) / (MAX_X - MIN_X) * screenx;
                p.y = (float)(p.y - MIN_Y) / (MAX_Y - MIN_Y) * screeny;
                env->CallVoidMethod(obj,callBack_method, p.x, p.y, true);

                p = points_buffer[1];
                p.x = (float)(p.x - MIN_X) / (MAX_X - MIN_X) * screenx;
                p.y = (float)(p.y - MIN_Y) / (MAX_Y - MIN_Y) * screeny;
                env->CallVoidMethod(obj,callBack_method, p.x, p.y, true);

            }
            Point p = points_buffer[N - 3];

            p.x = (float)(p.x - MIN_X) / (MAX_X - MIN_X) * screenx;
            p.y = (float)(p.y - MIN_Y) / (MAX_Y - MIN_Y) * screeny;

            env->CallVoidMethod(obj,callBack_method, p.x, p.y, true);


        }
    }
}

Mat subMat(Mat& par, Rect r)
{
    if (r.x < 0) r.x = 0;
    if (r.x >= par.cols) r.x = par.cols - 1;
    if (r.y < 0) r.y = 0;
    if (r.y >= par.rows) r.y = par.rows - 1;
    if (r.x + r.width >= par.cols) r.width = par.cols - 1 - r.x;
    if (r.y + r.height >= par.rows) r.height = par.rows - 1 - r.y;
    return par(r);
}

void calcPoint(Frame &frame, JNIEnv* env) {
    bool has_find_pattern;
    Rect patternRect;
    RotatedRect firstTouch;

    Mat input(GRID_RES_Y, GRID_RES_X, CV_32S, frame.capacity);
    input.convertTo(input, CV_32F);
    input = input / 50;

    int sum = matSum<float>(input);                    //计算该帧电容和作为判断帧可靠性的依据
    bool isDirty = (sum > DIRTY_SUM);//判断该帧是否足够可靠，以确定耳朵是否抬起

    /*
    /////////////////////////////////////check press//////////////////////////////////////////////
    if (!spinFlag && lastsum < touchSum + PRESS_THRESHOLD && sum >= touchSum + PRESS_THRESHOLD) {
        env->CallVoidMethod(obj,callBack_method, -1, -1, true);
        checked = CHECK_SUM;
        last_dirty = isDirty;
        lastsum = sum;
        usleep(1000000);
        return;
    }
    //////////////////////////////////////////////////////////////////////////////////////////////
    */
    if (isDirty)
    {
        Mat& binaryImage = Image[now];
        Mat& lastImage = Image[last];
        if (now)
            now = 0, last = 1;
        else now = 1, last = 0;

        Point result;
        resize(input, lanc, Size(), 5.0, 5.0, INTER_LANCZOS4);
        threshold(lanc, binaryImage, 70, 0, THRESH_TOZERO);        //gray
        binaryImage.convertTo(binaryImage, CV_8U);


        /*
        //check spin. here we go
        if (!swipeFlag) swipeFlag = checkSwipe(MAX_SWIPE_DIST);
        if ((!swipeFlag && checked < CHECK_SUM) || spinFlag) {
            checkSpin(env, sum, binaryImage);
            if (spinFlag) {
                last_dirty = isDirty;
                lastsum = sum;
                return;
            }
        }
        //here we stop
        */
        if (!last_dirty)                                         //触摸开始
        {
            //sendTCP(true);
            touchSum = sum;
            frame_count = 0;
            has_find_pattern = findPattern(binaryImage, patternRect, firstTouch);

            if (has_find_pattern)
            {
                Point want;
                calcFirstPoint(binaryImage, firstTouch, want);
                restrict(want, 0, lenx, 0, leny);

                trackerInit(tracker[0], binaryImage, patternRect);
                trackerInit(tracker[1], binaryImage, patternRect);
                pattern = binaryImage(patternRect);

                //record the first point
                firstPoint.x = want.x;
                firstPoint.y = want.y;

                result = last_result = Point(want.x, want.y);
                result.x = result.x * mulx;
                result.y = result.y * muly;
                if (result.x < MIN_X || result.x > MAX_X || result.y < MIN_Y || result.y > MAX_Y)
                {
                    if (result.y > MAX_Y || result.y < MIN_Y) {
                        //m_inject.vibrate();
                    }
                    return;
                }
                last_point[0].x = last_point[1].x = patternRect.x;
                last_point[0].y = last_point[1].y = patternRect.y;

            }
            else
            {
                env->CallVoidMethod(obj, callBack_method, -100000, 1, true);
                return;
            }
        }
        else                                            //触摸中
        {
            Point box_point, last_box_point, ptmp;
            pthread_create(&update_threads[0], NULL, update, NULL);
            pthread_create(&update_threads[1], NULL, update_last, NULL);
            pthread_join(update_threads[0], NULL);
            pthread_join(update_threads[1], NULL);
            if (succ)
            {
                if (!succ_last)
                {
                    Rect rect;
                    RotatedRect rr;
                    findPattern(binaryImage, rect, rr);
                    trackerInit(tracker[tracker_last], binaryImage, rect);
                    last_point[tracker_last] = Point(rect.x, rect.y);
                    frame_count = 0;
                }

                if (frame_count == KCF_REFRESH_INTERVAL)
                {
                    if (box.x >= 0 && box.y >= 0 && box.x + box.width <= binaryImage.cols && box.y + box.height <= binaryImage.rows)
                    {
                        Rect rect;
                        RotatedRect rr;
                        findPattern(binaryImage, rect, rr);
                        trackerInit(tracker[tracker_now], binaryImage, rect);

                        last_box_point = last_point[tracker_last];
                        box_point = Point(box_last.x, box_last.y);
                        if (tracker_now)
                            tracker_now = 0, tracker_last = 1;
                        else tracker_now = 1, tracker_last = 0;

                        frame_count = 0;
                        last_point[tracker_now] = box_point, last_point[tracker_last] = Point(rect.x, rect.y);

                        pattern = subMat(binaryImage, box_last);//
                    }
                    else
                    {
                        last_box_point = last_point[tracker_now];
                        box_point = Point(box.x, box.y);
                        last_point[tracker_now] = box_point, last_point[tracker_last] = Point(box_last.x, box_last.y);

                        pattern = subMat(binaryImage, box);//
                    }
                }
                else
                {
                    frame_count++;
                    last_box_point = last_point[tracker_now];
                    box_point = Point(box.x, box.y);
                    last_point[tracker_now] = box_point;
                    if (succ_last) last_point[tracker_last] = Point(box_last.x, box_last.y);

                    pattern = subMat(binaryImage, box);//
                }

                ptmp = box_point - last_box_point + last_result;
                restrict(ptmp, 0, lenx, 0, leny);
                result = ptmp;

                //result without rotated
                result.x = result.x * mulx;
                result.y = result.y * muly;
            }
            else
            {
                //判断卷起的情况
                Rect rect;
                RotatedRect rr;
                if (!findPattern(binaryImage, rect, rr))
                {
                    if (now)
                        now = 0, last = 1;
                    else now = 1, last = 0;
                    return;
                }

                frame_count = 0;
                if (succ_last)
                {
                    //cout << "switch\n";
                    trackerInit(tracker[tracker_now], binaryImage, rect);
                    last_box_point = last_point[tracker_last];
                    box_point = Point(box_last.x, box_last.y);

                    result = ptmp = box_point - last_box_point + last_result;
                    if (tracker_now)
                        tracker_now = 0, tracker_last = 1;
                    else tracker_now = 1, tracker_last = 0;
                    last_point[tracker_now] = box_point, last_point[tracker_last] = Point(rect.x, rect.y);

                    pattern = subMat(binaryImage, box_last);//
                }
                else
                {
                    //cout << "double\n";
                    trackerInit(tracker[tracker_last], binaryImage, rect);
                    trackerInit(tracker[tracker_now], binaryImage, rect);
                    pattern = binaryImage(rect);
                    box_point = Point(rect.x, rect.y);

                    last_box_point = matchPattern(lastImage, pattern);

                    result = ptmp = box_point - last_box_point + last_result;
                    last_point[tracker_now] = last_point[tracker_last] = box_point;
                }

                restrict(result, 0, lenx, 0, leny);

                //result without rotated
                result.x = result.x * mulx;
                result.y = result.y * muly;

            }

            if (result.x < MIN_X || result.x > MAX_X || result.y < MIN_Y || result.y > MAX_Y)
            {
                if (result.y > MAX_Y || result.y < MIN_Y) {
                    //m_inject.vibrate();
                }
                return;
            }

            last_result = ptmp;
        }

        /////////////////////////////////////check press//////////////////////////////////////////////
        //int patternSum = matSum<uchar>(pattern);
        if (pressFlag == 0 && !spinFlag && lastsum < touchSum + PRESS_THRESHOLD && sum >= touchSum + PRESS_THRESHOLD && !checkSwipe(MAX_PRESS_DIST)) {
            env->CallVoidMethod(obj, callBack_method, -1000, sum - touchSum, true);
            env->CallVoidMethod(obj, callBack_method, -10000, (int)press_dist, true);
            pressFlag = 80;
        }
        else if (pressFlag > 0 && !spinFlag) {
            env->CallVoidMethod(obj, callBack_method, -1000, sum - touchSum, true);
            if (sum < touchSum + PRESS_THRESHOLD && !checkSwipe(MAX_PRESS_DIST)) {
                env->CallVoidMethod(obj, callBack_method, -10000, (int)press_dist, true);
                pressFlag = 0;
                env->CallVoidMethod(obj, callBack_method, -1, -1, true);
                checked = CHECK_SUM;
                last_dirty = isDirty;
                lastsum = sum;
                return;
            }
            else
            {
                env->CallVoidMethod(obj, callBack_method, -10000, (int)press_dist, true);
                if (--pressFlag == 0)
                    pressFlag = -1;
            }
        }
        else
        {
            env->CallVoidMethod(obj, callBack_method, -1000, sum - touchSum, false);
            env->CallVoidMethod(obj, callBack_method, -10000, (int)press_dist, true);
        }
        //lastPatternSum = patternSum;
        //////////////////////////////////////////////////////////////////////////////////////////////

        sendPoint(env, false, result);
    }
    else                                               //触摸结束
    {
        env->CallVoidMethod(obj, callBack_method, -100000, sum, true);
        spinFlag = false;
        swipeFlag = false;

        if (last_dirty)
        {
            //////////////////////////////check press////////////////////////////////////
            if (pressFlag > 0) {
                if (!checkSwipe(MAX_PRESS_DIST)) {
                    env->CallVoidMethod(obj, callBack_method, -10000, (int)press_dist, true);
                    env->CallVoidMethod(obj, callBack_method, -1, -1, true);
                }
                else
                {
                    env->CallVoidMethod(obj, callBack_method, -10000, (int)press_dist, true);
                }
            }
            //////////////////////////////////////////////////////////////////
            sendPoint(env, true);
            //sendTCP(false);
        }
        pressFlag = 0;
        last_angle = -1, total_angle = 0, clkwise = 0, anticlkwise = 0;
    }

    last_dirty = isDirty;
    lastsum = sum;
}

long long swapLongLong(long long value)
{
    return (long long) (((value & 0x00000000000000FF) << 56) |
           ((value & 0x000000000000FF00) << 40) |
           ((value & 0x0000000000FF0000) << 24) |
           ((value & 0x00000000FF000000) << 8) |
           ((value & 0x000000FF00000000) >> 8) |
           ((value & 0x0000FF0000000000) >> 24) |
           ((value & 0x00FF000000000000) >> 40) |
           ((value & 0xFF00000000000000) >> 56));
}

void* handleFrame(void* args)
{
    JNIEnv *env;

    int status  = g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if(status < 0){
        status = g_jvm->AttachCurrentThread(&env, NULL);
        if(status < 0){
            env = NULL;
            LOGD("get Env Failed..");
            return NULL;
        }
    }

#ifdef FROMDEV
    int lastID = -1;
    while (true) {
        pthread_mutex_lock(&frames_mutex);
        if (frames.empty()) {
            pthread_mutex_unlock(&frames_mutex);
            usleep(100000);
            continue;
        }
        Frame *frame_current = &frames.back();
        pthread_mutex_unlock(&frames_mutex);

        if (frame_current->frameID == lastID) {
            lastID = frame_current->frameID;
            continue;
        }

        if (frame_current) {
            calcPoint(*frame_current, env);
        }
        lastID = frame_current->frameID;
    }
#endif

#ifdef FROMFILE
    frames.clear();
    ifstream fin(filename, ios::binary);

    if (fin) {
        LOGD("open File Succeeded..");
        Frame f;
        while (fin.read((char *)&f.capacity, sizeof(int) * GRID_RES_X * GRID_RES_Y))
            frames.push_back(f);

        fin.close();
    } else{
        LOGD("open File Failed..");
    }
    for (int i = 0; i < frames.size(); i += 2) {
        calcPoint(frames[i], env);
    }

    env->CallVoidMethod((jobject)args,notify_method);

#endif

    return NULL;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_diffrealtime_MainActivity_readDiffStart(JNIEnv *env, jobject instance) {


/*
    jclass cla = (env)->FindClass("com/example/diffshow/MainActivity");
    if(cla == 0){
        __android_log_print(ANDROID_LOG_DEBUG,TAG,"find class error");
        return ;
    }
    LOGD("Find Class...");
*/

    pthread_mutex_init(&frames_mutex, NULL);
    env->GetJavaVM(&g_jvm); // 保存java虚拟机对象
#ifdef REALTIME
    callBack_method = env->GetMethodID(env->GetObjectClass(instance),"processDiff","(IIZ)V");
#endif

#ifdef RECORD
    callBack_method = env->GetMethodID(env->GetObjectClass(instance),"record","([SZ)V");
#endif

    notify_method = env->GetMethodID(env->GetObjectClass(instance),"notifiedEnd","()V");

    if(callBack_method == 0){
        __android_log_print(ANDROID_LOG_DEBUG,TAG,"find callBack_method error");
        return ;
    }
    LOGD("Find Func...");
    obj = env->NewGlobalRef(instance);

#ifdef FROMDEV
    pthread_create(&thread_1, NULL, run, obj);
#endif

#if defined(FROMDEV) && defined(REALTIME)
    pthread_create(&thread_2, NULL, handleFrame, obj);
#endif

    LOGD("readDiffStart...");
}



extern "C"
JNIEXPORT void JNICALL
Java_com_example_diffrealtime_MainActivity_readDiffStop(JNIEnv *env, jobject instance) {

    // TODO
    if(pipefd[1] < 0)
        return;
    write(pipefd[1],"1",1);
    LOGD("readDiffStop...");
}

string jstring2str(JNIEnv* env, jstring jstr)
{
    char*   rtn   =   NULL;
    jclass   clsstring   =   env->FindClass("java/lang/String");
    jstring   strencode   =   env->NewStringUTF("GB2312");
    jmethodID   mid   =   env->GetMethodID(clsstring,   "getBytes",   "(Ljava/lang/String;)[B");
    jbyteArray   barr=   (jbyteArray)env->CallObjectMethod(jstr,mid,strencode);
    jsize   alen   =   env->GetArrayLength(barr);
    jbyte*   ba   =   env->GetByteArrayElements(barr,JNI_FALSE);
    if(alen   >   0)
    {
        rtn   =   (char*)malloc(alen+1);
        memcpy(rtn,ba,alen);
        rtn[alen]=0;
    }
    env->ReleaseByteArrayElements(barr,ba,0);
    if (rtn == NULL)
    {
        return "";
    }
    else {
        string stemp(rtn);
        free(rtn);
        return stemp;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_diffrealtime_MainActivity_readFile(JNIEnv *env, jobject instance, jstring fn) {
    filename = string("/sdcard/eartouch/") + jstring2str(env, fn);
    LOGD("shit");
    obj = env->NewGlobalRef(instance);
    pthread_create(&thread_2, NULL, handleFrame, obj);
}