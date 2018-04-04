package com.example.diffshow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Vector;

/**
 * Created by Administrator on 2018/3/24.
 */

public class DrawView extends View {

    static public Vector<Integer> points_x = new Vector<>();
    static public Vector<Integer> points_y = new Vector<>();

    private Paint p;

    public DrawView(Context context) {
        super(context);
        p = new Paint();
        p.setColor(Color.BLUE);
        p.setStrokeWidth(4.0f);
        p.setTextSize(30f);

        //画点
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(6.0f);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 创建笔刷
        for (int i = 0; i < points_x.size(); i++)
            canvas.drawPoint(points_x.get(i), points_y.get(i), p);//画一个点

    }

}
