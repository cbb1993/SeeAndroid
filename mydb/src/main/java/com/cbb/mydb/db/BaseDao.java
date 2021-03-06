package com.cbb.mydb.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import com.cbb.mydb.annotation.DbField;
import com.cbb.mydb.annotation.DbTable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by 坎坎.
 * Date: 2020/5/26
 * Time: 9:28
 * describe:
 */
public class BaseDao<T> implements IBaseDao<T> {
    //sql数据库引用
    private SQLiteDatabase database;
    //java bean 的class
    private Class<T> entityClass;
    // 表名
    private String tableName;
    //该表是否以及创建过
    private boolean isInit = false;

    // 该集合保存的是表名和字段的映射
    // 该集合的作用在于 只需要反射一次缓存下来 增删查改时不用每次都反射拿到对应关系
    private HashMap<String, Field> cacheMap = null;


    protected void init(SQLiteDatabase database, Class<T> entityClass) {
        this.database = database;
        this.entityClass = entityClass;
        if (!isInit) {
            // 开始建表
            // 是否有注解 有注解 表名称就取注解传入的  没有的就用类名
            if (entityClass.getAnnotation(DbTable.class) == null) {
                // 拿到类名
                tableName = entityClass.getSimpleName();
            } else {
                tableName = entityClass.getAnnotation(DbTable.class).value();
            }
            // 建表  create table if not exists user(id Integer,name TEXT,password TEXT)
            // 组件sql建表语句
            String sql = getCreateTableSql();
            Log.e("----------", "------>" + sql);
            // 执行数据库语句
            database.execSQL(sql);
            // 缓存
            initCacheMap();
            isInit = true;
        }
    }

    private void initCacheMap() {
        cacheMap = new HashMap<>();
        // 拿到所有的表名称
        String sql = "select * from " + tableName + " limit 1,0"; // 只有表结构的空表
        Cursor cursor = database.rawQuery(sql, null);
        String[] columnNames = cursor.getColumnNames();
        // 拿到类所有字段
        Field[] declaredFields = entityClass.getDeclaredFields();
        // 打开字段访问权限
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
        }
        //两层循环 找出对应关系
        for (String columnName : columnNames) {
            // 找到的字段
            Field columnField = null;
            for (Field declaredField : declaredFields) {
                String fieldName = null;
                if (declaredField.getAnnotation(DbField.class) == null) {
                    fieldName = declaredField.getName();
                } else {
                    fieldName = declaredField.getAnnotation(DbField.class).value();
                }
                if (fieldName.equals(columnName)) {
                    columnField = declaredField;
                    break;
                }
            }
            cacheMap.put(columnName, columnField);
        }
    }

    private String getCreateTableSql() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("create table if not exists ").append(tableName).append("(");
        // 根据class反射拿到类的字段
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            String columnName;
            // 判断字段名有没有注释，有就用注释传入的值 没有的话就用字段名
            if (field.getAnnotation(DbField.class) == null) {
                columnName = field.getName();
            } else {
                columnName = field.getAnnotation(DbField.class).value();
            }
            // 拿到字段类型
            Class type = field.getType();
            // 逐一判断字段类型
            String typeString;
            if (type == String.class) {
                typeString = " TEXT,";
            } else if (type == Integer.class) {
                typeString = " Integer,";
            } else if (type == Double.class) {
                typeString = " DOUBLE,";
            } else if (type == Long.class) {
                typeString = " BIGINT,";
            } else if (type == byte[].class) {
                typeString = " BLOB,";
            } else {
                //暂时不支持的类型
                return null;
            }
            buffer.append(columnName).append(" ").append(typeString);
        }
        // 删除最后多余的 , 号
        if (buffer.charAt(buffer.length() - 1) == ',') {
            buffer.deleteCharAt(buffer.length() - 1);
        }
        // 拼上结尾括号
        buffer.append(")");
        return buffer.toString();
    }

    @Override
    public long insert(T entity) {
        // 将对象的字段的值 和 数据库表名对应起来
        HashMap<String, String> values = getEntityValues(entity);
        ContentValues contentValues = getContentValues(values);
        long insert = database.insert(tableName, null, contentValues);
        return insert;
    }

    @Override
    public long update(T entity, T where) {
        HashMap<String, String> values = getEntityValues(entity);
        ContentValues contentValues = getContentValues(values);
        Condition condition = new Condition(where);
        int update = database.update(tableName, contentValues, condition.whereClause, condition.whereArgs);
        return update;
    }

    @Override
    public int delete(T where) {
        Condition condition = new Condition(where);
        int delete = database.delete(tableName, condition.whereClause, condition.whereArgs);
        return delete;
    }

    @Override
    public List<T> query(T where) {
        return query(where,null,1,100);
    }

    @Override
    public List<T> query(T where, String orderBy, Integer startIndex, Integer limit) {
        String limitString = null;
        if (startIndex != null && limit != null) {
            limitString = startIndex + " , " + limit;
        }
        ArrayList list = new ArrayList();
        Condition condition = new Condition(where);

        Cursor cursor = database.query(tableName, null,
                condition.whereClause, condition.whereArgs,null,null,
                orderBy, limitString);
        Object o = null;
        if(cursor!=null){
            while(cursor.moveToNext()){
                try {
                    o = where.getClass().newInstance();
                    Iterator<String> iterator = cacheMap.keySet().iterator();
                    while (iterator.hasNext()){
                        String next = iterator.next();
                        int columnIndex = cursor.getColumnIndex(next);
                        Field field=(Field)cacheMap.get(next);
                        Class type=field.getType();
                        if(columnIndex!=-1){
                            if(type==String.class){
                                field.set(o,cursor.getString(columnIndex));
                            }else if(type==Double.class){
                                field.set(o,cursor.getDouble(columnIndex));
                            }else if(type==Integer.class){
                                field.set(o,cursor.getInt(columnIndex));
                            }else if(type==Long.class){
                                field.set(o,cursor.getLong(columnIndex));
                            }else if(type==byte[].class){
                                field.set(o,cursor.getBlob(columnIndex));
                            }else{
                                continue;
                            }
                        }
                    }
                    list.add(o);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                }

            }


            cursor.close();
        }

        return list;
    }

    private ContentValues getContentValues(HashMap<String, String> values) {
        ContentValues contentValues =new ContentValues();
        for (String key : values.keySet()) {
            contentValues.put(key,values.get(key));
        }
        return contentValues;
    }

    private HashMap<String, String> getEntityValues(T entity) {
        HashMap<String, String> map = new HashMap<>();
        // 循环cacheMap 拿到所有的Field
        Set<String> keySet = cacheMap.keySet();
        for (String key : keySet) {
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            Field field = cacheMap.get(key);
            field.setAccessible(true);
            try {
                // 拿到字段的value
                Object o = field.get(entity);
                if (o == null) {
                    continue;
                }
                // 转为string
                String value = o.toString();
                map.put(key,value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return map;
    }

 // 将传入的对象 遍历属性 将有赋值的字段组合成下面两个属性
    class Condition{
        public String whereClause; // "name=?"
        public String[] whereArgs; // "new String[]{"hahh"}"

        public Condition(T where){
            //将表的列与实体类的值对应起来
            HashMap<String, String> entityValues = getEntityValues(where);
            // 计算到到whereClause 和 whereArgs
            List<String> list = new ArrayList<>();
            StringBuffer buffer = new StringBuffer();
            buffer.append("1==1");
            Iterator<String> iterator = entityValues.keySet().iterator();
            while(iterator.hasNext()){
                String next = iterator.next();
                if(next!=null){
                    buffer.append(" and "+next+"=?");
                    list.add(entityValues.get(next));
                }
            }
            whereClause = buffer.toString();
            String[] args = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                args[i] = list.get(i);
            }
            whereArgs = args;
        }
    }
}
