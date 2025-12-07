package com.example.android.notepad;

import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 笔记导出管理器，用于将笔记导出为文本文件
 */
public class ExportManager {
    private static final String TAG = "ExportManager";
    private final Context mContext;
    private final ContentResolver mContentResolver;

    /**
     * 构造函数
     * @param context 上下文
     */
    public ExportManager(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    /**
     * 导出所有笔记到文本文件
     * @return 是否导出成功
     */
    public boolean exportAllNotes() {
        // 检查外部存储是否可用
        if (!isExternalStorageWritable()) {
            Toast.makeText(mContext, R.string.export_error_storage_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }

        // 创建导出目录 - 使用适用于API 11的DOWNLOADS目录
        File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "NotePad");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Toast.makeText(mContext, R.string.export_error_create_directory, Toast.LENGTH_SHORT).show();
            return false;
        }

        // 创建导出文件
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File exportFile = new File(exportDir, "notes_export_" + timestamp + ".txt");

        try {
            FileWriter writer = new FileWriter(exportFile);

            // 查询所有笔记
            Cursor cursor = mContentResolver.query(
                    NotePad.Notes.CONTENT_URI,
                    new String[] {
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE,
                            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                    },
                    null,
                    null,
                    NotePad.Notes.DEFAULT_SORT_ORDER
            );

            if (cursor != null) {
                int noteCount = 0;
                
                // 写入文件头信息
                writer.write(mContext.getString(R.string.export_file_header) + "\n");
                writer.write("=====================================\n\n");

                // 遍历笔记并写入文件
                while (cursor.moveToNext()) {
                    String title = cursor.getString(0);
                    String content = cursor.getString(1);
                    long createDate = cursor.getLong(2);
                    long modifyDate = cursor.getLong(3);

                    // 写入单个笔记
                    writer.write("【 " + title + " 】\n");
                    writer.write(mContext.getString(R.string.export_created_date) + ": " + 
                            formatDate(createDate) + "\n");
                    writer.write(mContext.getString(R.string.export_modified_date) + ": " + 
                            formatDate(modifyDate) + "\n");
                    writer.write("-------------------------------------\n");
                    writer.write(content + "\n\n");
                    writer.write("=====================================\n\n");

                    noteCount++;
                }

                cursor.close();
                writer.close();

                // 显示导出成功消息
                String successMessage = String.format(
                        mContext.getString(R.string.export_success), 
                        noteCount, exportFile.getAbsolutePath());
                Toast.makeText(mContext, successMessage, Toast.LENGTH_LONG).show();
                
                return true;
            } else {
                Toast.makeText(mContext, R.string.export_error_no_notes, Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "导出笔记失败: " + e.getMessage());
            Toast.makeText(mContext, R.string.export_error_io_exception, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 导出指定分类的笔记
     * @param categoryId 分类ID
     * @return 是否导出成功
     */
    public boolean exportNotesByCategory(long categoryId) {
        // 检查外部存储是否可用
        if (!isExternalStorageWritable()) {
            Toast.makeText(mContext, R.string.export_error_storage_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }

        // 获取分类名称
        String categoryName = getCategoryName(categoryId);

        // 创建导出目录 - 使用适用于API 11的DOWNLOADS目录
        File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "NotePad");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Toast.makeText(mContext, R.string.export_error_create_directory, Toast.LENGTH_SHORT).show();
            return false;
        }

        // 创建导出文件
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "notes_export_" + categoryName + "_" + timestamp + ".txt";
        File exportFile = new File(exportDir, fileName);

        try {
            FileWriter writer = new FileWriter(exportFile);

            // 查询指定分类的笔记
            Uri categoryUri = ContentUris.withAppendedId(
                    Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                    categoryId
            );
            
            Cursor cursor = mContentResolver.query(
                    Uri.withAppendedPath(categoryUri, "notes"),
                    new String[] {
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE,
                            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                    },
                    null,
                    null,
                    NotePad.Notes.DEFAULT_SORT_ORDER
            );

            if (cursor != null) {
                int noteCount = 0;
                
                // 写入文件头信息
                writer.write(mContext.getString(R.string.export_file_header) + "\n");
                writer.write(mContext.getString(R.string.export_category_header) + ": " + categoryName + "\n");
                writer.write("=====================================\n\n");

                // 遍历笔记并写入文件
                while (cursor.moveToNext()) {
                    String title = cursor.getString(0);
                    String content = cursor.getString(1);
                    long createDate = cursor.getLong(2);
                    long modifyDate = cursor.getLong(3);

                    // 写入单个笔记
                    writer.write("【 " + title + " 】\n");
                    writer.write(mContext.getString(R.string.export_created_date) + ": " + 
                            formatDate(createDate) + "\n");
                    writer.write(mContext.getString(R.string.export_modified_date) + ": " + 
                            formatDate(modifyDate) + "\n");
                    writer.write("-------------------------------------\n");
                    writer.write(content + "\n\n");
                    writer.write("=====================================\n\n");

                    noteCount++;
                }

                cursor.close();
                writer.close();

                // 显示导出成功消息
                String successMessage = String.format(
                        mContext.getString(R.string.export_success), 
                        noteCount, exportFile.getAbsolutePath());
                Toast.makeText(mContext, successMessage, Toast.LENGTH_LONG).show();
                
                return true;
            } else {
                Toast.makeText(mContext, R.string.export_error_no_notes, Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "导出笔记失败: " + e.getMessage());
            Toast.makeText(mContext, R.string.export_error_io_exception, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 导出单个笔记
     * @param noteUri 笔记URI
     * @return 是否导出成功
     */
    public boolean exportSingleNote(Uri noteUri) {
        // 检查外部存储是否可用
        if (!isExternalStorageWritable()) {
            Toast.makeText(mContext, R.string.export_error_storage_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }

        // 创建导出目录 - 使用适用于API 11的DOWNLOADS目录
        File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "NotePad");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Toast.makeText(mContext, R.string.export_error_create_directory, Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            // 查询单个笔记
            Cursor cursor = mContentResolver.query(
                    noteUri,
                    new String[] {
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_NOTE,
                            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                    },
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                String content = cursor.getString(1);
                long createDate = cursor.getLong(2);
                long modifyDate = cursor.getLong(3);

                cursor.close();

                // 创建导出文件（替换文件名中的非法字符）
                String safeTitle = title.replaceAll("[^\\w\\s.-]", "_");
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File exportFile = new File(exportDir, safeTitle + "_" + timestamp + ".txt");

                FileWriter writer = new FileWriter(exportFile);

                // 写入笔记信息
                writer.write("【 " + title + " 】\n");
                writer.write(mContext.getString(R.string.export_created_date) + ": " + 
                        formatDate(createDate) + "\n");
                writer.write(mContext.getString(R.string.export_modified_date) + ": " + 
                        formatDate(modifyDate) + "\n");
                writer.write("-------------------------------------\n");
                writer.write(content + "\n");

                writer.close();

                // 显示导出成功消息
                String successMessage = String.format(
                        mContext.getString(R.string.export_single_success), 
                        title, exportFile.getAbsolutePath());
                Toast.makeText(mContext, successMessage, Toast.LENGTH_LONG).show();
                
                return true;
            } else {
                Toast.makeText(mContext, R.string.export_error_note_not_found, Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "导出笔记失败: " + e.getMessage());
            Toast.makeText(mContext, R.string.export_error_io_exception, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 检查外部存储是否可写
     * @return 是否可写
     */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * 格式化日期
     * @param timestamp 时间戳
     * @return 格式化后的日期字符串
     */
    private String formatDate(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    /**
     * 获取分类名称
     * @param categoryId 分类ID
     * @return 分类名称
     */
    private String getCategoryName(long categoryId) {
        // 使用正确的分类URI格式: categories/#
        String authority = NotePad.AUTHORITY;
        Uri categoryUri = Uri.parse("content://" + authority + "/categories/" + categoryId);
        
        Cursor cursor = mContentResolver.query(
                categoryUri,
                new String[] { "title" },  // 直接使用表中的列名
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(0);
            cursor.close();
            return name;
        }

        return mContext.getString(R.string.default_category_name);
    }
}