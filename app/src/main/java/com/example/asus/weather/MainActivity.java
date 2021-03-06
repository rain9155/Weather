package com.example.asus.weather;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.example.asus.weather.Temp.Temp;
import com.example.asus.weather.adapter.FragAdapter;
import com.example.asus.weather.db.SQLDatabase;
import com.example.asus.weather.file.FileDatabase;
import com.example.asus.weather.file.SPFDatabase;
import com.example.asus.weather.fragment.WeatherFragment;
import com.example.asus.weather.json.Location;
import com.example.asus.weather.services.WeatherUpdataService;
import com.example.asus.weather.unit.ActivityCollector;
import com.example.asus.weather.unit.HttpUnity;
import com.example.asus.weather.unit.JSONUnity;
import com.example.asus.weather.unit.MyPopupWindow;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ViewPager.OnPageChangeListener {

    ArrayList<Fragment> fragmentArrayList;//天气界面集合
    ViewPager viewPager;//页面切换sp
    LinearLayout dotContainer;//底部导航栏圆点容器
    FragAdapter fragAdapter;
    ImageView imageViewAdd;
    Toolbar toolbar;
    ImageView imageViewThree;//底部弹出菜单按钮

    private ArrayList<Location> locationArrayList;
    private int currentDorPosition = 0;//当前圆点位置
    private String UPDATAALL = "com.example.asus.weather.UPDATAALL";
    private LocationClient locationClient;//定位
    public static boolean IS_NETWORK_AVAILABLE = true;
    private NetworkChangeReceiver networkChangeReceiver;
    private IntentFilter intentFilter;
    private SQLDatabase sqlDatabase;
    private int pagePosition;
    private ArrayList<String> pageId = new ArrayList<>();
    private MyPopupWindow myPopupWindow;//弹窗

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCollector.addActivity(this);
        sqlDatabase = new SQLDatabase(MainActivity.this, "Weather.db", null, 1);
        networkChangeReceiver = new NetworkChangeReceiver();
        intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(networkChangeReceiver, intentFilter);
        locationClient = new LocationClient(getApplicationContext());
        locationClient.registerLocationListener(new MyLocationListener());
        if(Build.VERSION.SDK_INT >= 21){
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }else {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            ViewGroup decorViewGroup = (ViewGroup) window.getDecorView();
            View statusBarView = new View(window.getContext());
            int statusBarHeight = getStatusBarHeight(window.getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, statusBarHeight);
            params.gravity = Gravity.TOP;
            statusBarView.setLayoutParams(params);
            statusBarView.setBackgroundColor(Color.TRANSPARENT);
            decorViewGroup.addView(statusBarView);
        }
        setContentView(R.layout.activity_main);

        imageViewAdd = findViewById(R.id.image_add);
        viewPager = findViewById(R.id.viewpager);
        toolbar = findViewById(R.id.main_toolbar);
        imageViewThree = findViewById(R.id.image_three_point);
        dotContainer = findViewById(R.id.linear_dot_container);

        fragmentArrayList = new ArrayList<>();
        setSupportActionBar(toolbar);
        imageViewAdd.setOnClickListener(this);
        imageViewThree.setOnClickListener(this);
        viewPager.addOnPageChangeListener(this);

        openLocation();

        Intent intent = getIntent();
        String address = intent.getStringExtra("address");
        ArrayList<String> arrayList = quryFromSQL("Address", "address");
        if(pageId != null || pageId.size() != 0){
            pageId.clear();
        }
        if (!TextUtils.isEmpty(address)) {//从其他活动跳转来
            if(fragmentArrayList.size() != 0){
                fragmentArrayList.clear();
            }

            fragmentArrayList.add(WeatherFragment.newFragment(address));
            pageId.add(address);
            for(String s : arrayList){
                if(s.compareTo(address) != 0){
                    fragmentArrayList.add(WeatherFragment.newFragment(s));
                    pageId.add(s);
                }
            }

        }else {//第一次进入程序
            if(arrayList.size() != 0 && arrayList != null){
                String location = SPFDatabase.extractData("location");
                if(location != null){
                    int k = 1;
                    for(String s : arrayList){
                        if(s.compareTo(location) == 0)  k = 0;
                    }
                    if(k == 1){
                        for(String s : arrayList){
                            fragmentArrayList.add(WeatherFragment.newFragment(s));
                            pageId.add(s);
                        }
                    }else {//本地天气置顶
                        fragmentArrayList.add(WeatherFragment.newFragment(location));
                        pageId.add(location);
                        for(String s : arrayList){
                            if(s.compareTo(location) != 0){
                                fragmentArrayList.add(WeatherFragment.newFragment(s));
                                pageId.add(s);
                            }
                        }
                    }
                }
            }
        }

        if(fragmentArrayList.size() != 0){
            fragAdapter = new FragAdapter(getSupportFragmentManager(), fragmentArrayList);
            viewPager.setAdapter(fragAdapter);
            fragAdapter.notifyDataSetChanged();
            for (Fragment fragment : fragmentArrayList){
                Temp.weatherFragmentTreeMap.put(fragment.getArguments().getString("key"), (WeatherFragment) fragment);
            }
        }

         /* 设置底部圆点 */
        if(dotContainer.getChildCount() != 0){
            dotContainer.removeAllViews();
        }
        if(arrayList.size() == 1){
            dotContainer.setVisibility(View.GONE);
        }
        for(int i = 0; i < arrayList.size(); i++){
            ImageView imageView = new ImageView(MainActivity.this);
            if(i == currentDorPosition){
                imageView.setImageResource(R.drawable.guide_dot_black);
            }else {
                imageView.setImageResource(R.drawable.guide_dot_withe);
            }
            dotContainer.addView(imageView);
        }

        //弹出菜单
        myPopupWindow = new MyPopupWindow(MainActivity.this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,  new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()){
                    case R.id.text_location:
                        Temp.IS_LOCATION = 1;
                        locationClient.requestLocation();
                        myPopupWindow.dismiss();
                        break;
                    case R.id.text_shared:
//                        Intent intent = new Intent(Intent.ACTION_SEND);
//                        intent.setType("text/plain");
//                        intent.putExtra(Intent.EXTRA_SUBJECT, "我是标题");
//                        Now now = Temp.treeMapWeatherAddress.get(pageId.get(pagePosition));
//                        intent.putExtra(Intent.EXTRA_TEXT, now.nowText + " / "  + now.nowTemperature + "℃" + "\n" + now.update);
//                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        startActivity(Intent.createChooser(intent, "分享到"));
                        Bitmap bitmap = shotScrollView(Temp.weatherFragmentTreeMap.get(pageId.get(pagePosition)).getScrollView());
                        FileDatabase.saveBitmap("bitmap", bitmap);
                        Bitmap bitmap1 = FileDatabase.loadBitmap("bitmap");
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        File file = new File(MyApplication.getContext().getFilesDir(), "bitmap");
                        if(file != null && file.exists()) {
                            intent.setType("image/*");
                            //由文件得到路径
                            Uri uri = Uri.fromFile(file);
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                        }else{
                            intent.setType("text/plain");
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(Intent.createChooser(intent, "分享到"));
                        myPopupWindow.dismiss();
                        break;
                    default:
                        break;
                }
            }
        });

        myPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                //  changeBackgoundAlpha(1.0f);
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationClient.stop();
        unregisterReceiver(networkChangeReceiver);
        Intent intentService = new Intent(this, WeatherUpdataService.class);
        startService(intentService);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Temp.IS_STARTACTIVITY = 1;
        ActivityCollector.finishAll();
    }

    /**
     * 处理从上一个活动返回时的逻辑
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case 1:
                if(RESULT_OK == resultCode) {
                    String result = data.getStringExtra("data_return");
                    if (result.compareTo("reset") == 0) {
                        ArrayList<String> arrayList = quryFromSQL("Address", "address");
                        if (arrayList.size() != 0) {
                            for(String s : Temp.deleteArrayList){
                                fragAdapter.deleteItem(s);
                                fragmentArrayList.remove(s);
                                pageId.remove(s);
                                Temp.weatherFragmentTreeMap.remove(s);
                                fragAdapter.notifyDataSetChanged();
                            }
                        }

                        /* 设置底部圆点 */
                        if(dotContainer.getChildCount() != 0){
                            dotContainer.removeAllViews();
                        }else {
                            dotContainer.setVisibility(View.VISIBLE);
                        }
                        if(arrayList.size() == 1){
                            dotContainer.setVisibility(View.GONE);
                        }else {
                            dotContainer.setVisibility(View.VISIBLE);
                        }
                        for(int i = 0; i < arrayList.size(); i++){
                            ImageView imageView = new ImageView(MainActivity.this);
                            if(i == currentDorPosition){
                                imageView.setImageResource(R.drawable.guide_dot_black);
                            }else {
                                imageView.setImageResource(R.drawable.guide_dot_withe);
                            }
                            dotContainer.addView(imageView);
                        }
                    }
                }
                break;
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        pagePosition = position;
        currentDorPosition = position;
        for(int i = 0; i < dotContainer.getChildCount(); i++){
            ImageView imageView = (ImageView)dotContainer.getChildAt(i);
            if(i == currentDorPosition){
                imageView.setImageResource(R.drawable.guide_dot_black);
            }else {
                imageView.setImageResource(R.drawable.guide_dot_withe);
            }
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    /**
     * 定位监听器
     */
    public class MyLocationListener extends BDAbstractLocationListener {

        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            if (bdLocation == null) {
                return;
            }
            String data = bdLocation.getCity();//中文城市

            Log.d("rain", "onReceiveLocation: "  + bdLocation.getDistrict() + bdLocation.getCountryCode());
            if(data == null){
                data = "广州";
            }

            String address = "https://api.seniverse.com/v3/location/search.json?key=v2bxdf4yegclkkns&q=" + returnData(data);
            new MyLocationAsyncTask().execute(address);

            //Toast.makeText(MainActivity.this, "定位到" + bdLocation.getDistrict() + bdLocation.getCity() + bdLocation.getCountry(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 各种控件点击事件
     * @param v
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.image_add:
                Intent intent = new Intent(MainActivity.this, CityManageActivity.class);
                startActivityForResult(intent, 1);
                break;
            case R.id.image_three_point:
                myPopupWindow.showOnView(toolbar);
                //changeBackgoundAlpha(0.5f);
                break;
            default:
                break;
        }
    }

    /**
     * 定位设置
     */
    private void initLocationClient() {
        LocationClientOption option = new LocationClientOption();
        /* 每1小时更新一次定位 */
        option.setScanSpan(3600000);
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setIsNeedAddress(true);
        option.setOpenGps(true);

        locationClient.setLocOption(option);
    }

    /**
     * 开启定位
     */
    private void openLocation() {
        initLocationClient();
        locationClient.start();
    }

    /**
     * 启动活动
     *
     * @param context
     */
    public static void activityStart(Context context, String address) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("address", address);
        context.startActivity(intent);
    }

    /**
     * 接受系统网络广播
     * Created by asus on 2018/4/27.
     */
    class NetworkChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isAvailable()) {
                IS_NETWORK_AVAILABLE = true;
            } else {
                IS_NETWORK_AVAILABLE = false;
                Toast.makeText(context, "网络不可用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 向数据库中插入数据
     *
     * @param bookName 表名
     * @param colName  列名
     * @param data     数据
     */
    private void insertInSQL(String bookName, String colName, String data) {
        SQLiteDatabase db = sqlDatabase.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(colName, data);
        db.insert(bookName, null, contentValues);
        contentValues.clear();
    }

    /**
     * 从数据库中删除数据
     * @param bookName 表名
     * @param deleteData 要删除的数据
     */
    private void deleteFromSQL(String bookName, String where, String deleteData){
        SQLiteDatabase db = sqlDatabase.getWritableDatabase();
        db.delete(bookName, where, new String[]{deleteData});
    }

    /**
     * 从数据库中查询数据
     * @param bookName 表名
     * @param colName  列名
     * @return NewsID或ImageUrl的ArrayList
     */
    private ArrayList<String> quryFromSQL(String bookName, String colName) {
        ArrayList arrayList = new ArrayList();
        SQLiteDatabase db = sqlDatabase.getReadableDatabase();
        Cursor cursor = db.query(bookName, null, null, null, null, null, null);
        if (cursor.moveToFirst()) {
            do {
                String record = cursor.getString(cursor.getColumnIndex(colName));
                arrayList.add(record);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return arrayList;
    }

    /**
     * 城市搜索的位置请求
     */
    private class MyLocationAsyncTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            String address = params[0];
            String response = HttpUnity.sendHttpRequest(address);
            return response;
        }

        @Override
        protected void onPostExecute(String s) {
            if (!TextUtils.isEmpty(s)){
                locationArrayList = JSONUnity.praseLocationResponse(s);
                if(locationArrayList.size() != 0 && locationArrayList != null){
                    String address = locationArrayList.get(0).locationId;//返回来的位置id
                    ArrayList<String> arrayList = quryFromSQL("Address", "address");
                    Temp.location = address;
                    SPFDatabase.preferenceData("location", address);

                    if(Temp.IS_STARTACTIVITY == 1){//第一次进入主活动
                        Temp.IS_STARTACTIVITY = 0;
                        if(arrayList.size() == 0 || arrayList == null){// 程序第一次安装进入程序，数据库没有数据
                            if (fragmentArrayList.size() != 0){
                                fragmentArrayList.clear();
                            }
                            insert(arrayList, address);
                            fragmentArrayList.add(WeatherFragment.newFragment(address));
                            pageId.add(address);
                            Temp.weatherFragmentTreeMap.put(address,(WeatherFragment) WeatherFragment.newFragment(address));
                            fragAdapter = new FragAdapter(getSupportFragmentManager(), fragmentArrayList);
                            viewPager.setAdapter(fragAdapter);
                            fragAdapter.notifyDataSetChanged();
                        }
                    }

                    if(Temp.IS_LOCATION == 1){//定位
                        Temp.IS_LOCATION = 0;
                        if(arrayList.size() != 0){
                            insert(arrayList, address);
                            int k = 1;
                            for(String str : arrayList){
                                if(str.compareTo(address) == 0)
                                    k = 0;
                            }
                            if(k == 1){//没有定位过
                                if((pageId != null || pageId.size() != 0) && pageId.size() != 1){
                                    ArrayList<String> tempList = pageId;
                                    pageId.clear();
                                    pageId.add(address);
                                    pageId.addAll(tempList);
                                }else {
                                    pageId.add(address);
                                }
                                Temp.weatherFragmentTreeMap.put(address,(WeatherFragment) WeatherFragment.newFragment(address));
                                fragAdapter.addItem(address);
                                viewPager.setCurrentItem(0);
                                fragAdapter.notifyDataSetChanged();
                                /* 设置底部圆点 */
                                if(dotContainer.getChildCount() != 0){
                                    dotContainer.removeAllViews();
                                }
                                ArrayList<String> arrayList1 = quryFromSQL("Address", "address");
                                if(arrayList1.size() == 1){
                                    dotContainer.setVisibility(View.GONE);
                                }else {
                                    dotContainer.setVisibility(View.VISIBLE);
                                }
                                for(int i = 0; i < arrayList1.size(); i++){
                                    ImageView imageView = new ImageView(MainActivity.this);
                                    if(i == currentDorPosition){
                                        imageView.setImageResource(R.drawable.guide_dot_black);
                                    }else {
                                        imageView.setImageResource(R.drawable.guide_dot_withe);
                                    }
                                    dotContainer.addView(imageView);
                                }
                            }else {//已经定位过
                                Toast.makeText(MainActivity.this, "已有定位", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    Intent intent1 = new Intent();
                    intent1.setAction(UPDATAALL);
                    sendBroadcast(intent1);
                }
            }
        }
    }

    /**
     * 插入解析过的数据到数据库,查看数据库有没有
     * @param arrayList
     */
    private void insert(ArrayList<String> arrayList, String data){
        int k = 1;
        for(String s : arrayList){
            if(s.compareTo(data) == 0){
                k = 0;
            }
        }
        if(k == 1){
            for(String s : arrayList){
                deleteFromSQL("Address", "address == ?", s);
            }
            insertInSQL("Address", "address", data);
            for(String s : arrayList){
                insertInSQL("Address", "address", s);
            }
        }
    }

    /**
     * 返回解析过的城市数据
     * @param oldAddress
     * @return
     */
    private String returnData(String oldAddress){
        String newAddress = oldAddress;
        if(oldAddress.contains("市")){
            newAddress = oldAddress.replace("市", "");
        }if(oldAddress.contains("区")){
            newAddress = oldAddress.replace("区", "");
        }if(oldAddress.contains("县")){
            newAddress = oldAddress.replace("县", "");
        }
        String data = encode(newAddress);
        return data;
    }

    /**
     * 编码
     */
    private String encode(String string){
        String str = new String();
        try {
            str = URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return str;
    }

    /**
     * 获取系统状态栏高度
     * @param context
     * @return
     */
    private int getStatusBarHeight(Context context) {
        int statusBarHeight = 0;
        Resources res = context.getResources();
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = res.getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    /**
     * 截取ScrollView图片
     * @param scrollView
     * @return
     */
    public static Bitmap shotScrollView(ScrollView scrollView) {
        int h = 0;
        Bitmap bitmap = null;
        for (int i = 0; i < scrollView.getChildCount(); i++) {
            h += scrollView.getChildAt(i).getHeight();
            scrollView.getChildAt(i).setBackgroundColor(Color.parseColor("#ffffff"));
        }
        bitmap = Bitmap.createBitmap(scrollView.getWidth(), h, Bitmap.Config.RGB_565);
        final Canvas canvas = new Canvas(bitmap);
        scrollView.draw(canvas);
        return bitmap;
    }

    /**
     * 改变背景透明度
     */
    private void changeBackgoundAlpha(String color){
//        WindowManager.LayoutParams lp = getWindow().getAttributes();
//        lp.alpha = alpha;
//        getWindow().setAttributes(lp);
    }
}
