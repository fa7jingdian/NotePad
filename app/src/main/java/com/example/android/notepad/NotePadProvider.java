/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 * Provides access to a database of notes. Each note has a title, the note
 * itself, a creation date and a modified data.
 */
public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    // Used for debugging and logging
    private static final String TAG = "NotePadProvider";

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 5;

    /**
     * A projection map used to select columns from the database
     */
    private static final HashMap<String, String> sNotesProjectionMap;
    private static final HashMap<String, String> sCategoriesProjectionMap;

    /**
     * A projection map used to select columns from the database
     */
    private static final HashMap<String, String> sLiveFolderProjectionMap;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,               // Projection position 0, the note's id
            NotePad.Notes.COLUMN_NAME_NOTE,  // Projection position 1, the note's content
            NotePad.Notes.COLUMN_NAME_TITLE, // Projection position 2, the note's title
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */
    // The incoming URI matches the Notes URI pattern
    private static final int NOTES = 1;

    // The incoming URI matches the Note ID URI pattern
    private static final int NOTE_ID = 2;

    // The incoming URI matches the Live Folder URI pattern
    private static final int LIVE_FOLDER_NOTES = 3;
    
    // Categories URI 匹配器
    private static final int CATEGORIES = 4;
    private static final int CATEGORY_ID = 5;
    private static final int NOTES_BY_CATEGORY = 6;
    // 添加对notes/categories URI的支持
    private static final int NOTES_CATEGORIES = 7;

    /**
     * A UriMatcher instance
     */
    private static final UriMatcher sUriMatcher;

    // Handle to a new DatabaseHelper.
    private DatabaseHelper mOpenHelper;

    /*
     * 静态初始化块，用于实例化和设置静态对象
     */
    static {

        /*
         * Creates and initializes the URI matcher
         */
        // Create a new instance
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Add a pattern that routes URIs terminated with "notes" to a NOTES operation
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);

        // Add a pattern that routes URIs terminated with "notes" plus an integer
        // to a note ID operation
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        // Add a pattern that routes URIs terminated with live_folders/notes to a
        // live folder operation
        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);
        
        // 分类相关的URI
        sUriMatcher.addURI(NotePad.AUTHORITY, "categories", CATEGORIES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "categories/#", CATEGORY_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "categories/#/notes", NOTES_BY_CATEGORY);
        // 添加对notes/categories URI的支持，用于从notes路径访问分类列表
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/categories", NOTES_CATEGORIES);
        // 添加对notes/categories/#/notes URI的支持，用于从notes路径访问指定分类的笔记
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/categories/#/notes", NOTES_BY_CATEGORY);

        /*
         * Creates and initializes a projection map that returns all columns
         */

        // Creates a new projection map instance. The map returns a column name
        // given a string. The two are usually equal.
        sNotesProjectionMap = new HashMap<String, String>();

        // Maps the string "_ID" to the column name "_ID" with table name prefix to avoid ambiguity
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID);

        // Maps "title" to "title" with table name prefix
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE);

        // Maps "note" to "note" with table name prefix
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE);

        // Maps "created" to "created" with table name prefix
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_CREATE_DATE);

        // Maps "modified" to "modified" with table name prefix
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
                
        // 添加分类ID，使用表名前缀避免歧义
        sNotesProjectionMap.put("category_id", NotePad.Notes.TABLE_NAME + ".category_id");
        sNotesProjectionMap.put("category_title", Categories.TABLE_NAME + ".title AS category_title");
        
        // 添加带表前缀的列名映射，以支持"notes.title"这样的查询
        sNotesProjectionMap.put(NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE);
        sNotesProjectionMap.put(NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID);
        sNotesProjectionMap.put(NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_CREATE_DATE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_CREATE_DATE);
        sNotesProjectionMap.put(NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
        sNotesProjectionMap.put(NotePad.Notes.TABLE_NAME + ".category_id", NotePad.Notes.TABLE_NAME + ".category_id");
        sNotesProjectionMap.put(NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE);
        
        // 分类表的投影映射
        sCategoriesProjectionMap = new HashMap<String, String>();
        sCategoriesProjectionMap.put(Categories._ID, Categories._ID);
        sCategoriesProjectionMap.put(Categories.COLUMN_NAME_TITLE, Categories.COLUMN_NAME_TITLE);

        /*
         * Creates an initializes a projection map for handling Live Folders
         */

        // Creates a new projection map instance
        sLiveFolderProjectionMap = new HashMap<String, String>();

        // Maps "_ID" to "_ID AS _ID" for a live folder
        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        // Maps "NAME" to "title AS NAME"
        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
            LiveFolders.NAME);
    }

    /**
    * 分类表常量
    */
   public static final class Categories {
       // 分类表名
       public static final String TABLE_NAME = "categories";
       
       // 分类列名
       public static final String _ID = "_id";
       public static final String COLUMN_NAME_TITLE = "title";
       
       // 默认分类ID
       public static final long DEFAULT_CATEGORY_ID = 1;
       
       // 内容类型
       public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note_category";
       public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note_category";
       
       // 默认排序
       public static final String DEFAULT_SORT_ORDER = "title";
   }

   /**
    * This class helps open, create, and upgrade the database file. Set to package visibility
    * for testing purposes.
    */
   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {

           // calls the super constructor, requesting the default cursor factory.
           super(context, DATABASE_NAME, (SQLiteDatabase.CursorFactory) null, DATABASE_VERSION);
       }

       /**
        *
        * Creates the underlying database with table name and column names taken from the
        * NotePad class.
        */
       @Override
       public void onCreate(SQLiteDatabase db) {
           // 创建分类表
           db.execSQL("CREATE TABLE " + Categories.TABLE_NAME + " (" +
                   Categories._ID + " INTEGER PRIMARY KEY, " +
                   Categories.COLUMN_NAME_TITLE + " TEXT NOT NULL" +
                   ");");
           
           // 创建默认分类
           db.execSQL("INSERT INTO " + Categories.TABLE_NAME + " VALUES (1, '默认分类');");
           
           // 创建笔记表
           db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " (" 
                   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                   + "category_id INTEGER DEFAULT 1, " +
                   "FOREIGN KEY (category_id) REFERENCES " + Categories.TABLE_NAME + "(_id)" +
                   ");");
       }

       /**
        *
        * Demonstrates that the provider must consider what happens when the
        * underlying datastore is changed. In this sample, the database is upgraded the database
        * by destroying the existing data.
        * A real application should upgrade the database in place.
        */
       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

           // Logs that the database is being upgraded
           Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                   + newVersion + ", which will destroy all old data");

           // 升级数据库时，如果存在分类表，则删除
           db.execSQL("DROP TABLE IF EXISTS " + Categories.TABLE_NAME);
           
           // Kills the table and existing data
           db.execSQL("DROP TABLE IF EXISTS notes");

           // Recreates the database with a new version
           onCreate(db);
       }
       
       @Override
       public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
           // Logs that the database is being downgraded
           Log.w(TAG, "Downgrading database from version " + oldVersion + " to "
                   + newVersion + ", which will destroy all old data");
           
           // For this simple app, we'll handle downgrade the same way as upgrade
           onUpgrade(db, oldVersion, newVersion);
       }
   }

   /**
    *
    * Initializes the provider by creating a new DatabaseHelper. onCreate() is called
    * automatically when Android creates the provider in response to a resolver request from a
    * client.
    */
   @Override
   public boolean onCreate() {

       // Creates a new helper object. Note that the database itself isn't opened until
       // something tries to access it, and it's only created if it doesn't already exist.
       mOpenHelper = new DatabaseHelper(getContext());

       // Assumes that any failures will be reported by a thrown exception.
       return true;
   }

   /**
    * This method is called when a client calls
    * {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}.
    * Queries the database and returns a cursor containing the results.
    *
    * @return A cursor containing the results of the query. The cursor exists but is empty if
    * the query returns no results or an exception occurs.
    * @throws IllegalArgumentException if the incoming URI pattern is invalid.
    */
   @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        // Constructs a new query builder
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        int match = sUriMatcher.match(uri);

        /**
         * Choose the projection and adjust the "where" clause based on URI pattern-matching.
         */
        switch (match) {
            // If the incoming URI is for notes, chooses the Notes projection with category join
            case NOTES:
                qb.setTables(NotePad.Notes.TABLE_NAME + " LEFT JOIN " + Categories.TABLE_NAME + 
                        " ON " + NotePad.Notes.TABLE_NAME + ".category_id = " + Categories.TABLE_NAME + "." + Categories._ID);
                qb.setProjectionMap(sNotesProjectionMap);
                break;

            /* If the incoming URI is for a single note identified by its ID, chooses the
             * note ID projection, and appends "_ID = <noteID>" to the where clause, so that
             * it selects that single note
             */
            case NOTE_ID:
                qb.setTables(NotePad.Notes.TABLE_NAME + " LEFT JOIN " + Categories.TABLE_NAME + 
                        " ON " + NotePad.Notes.TABLE_NAME + ".category_id = " + Categories.TABLE_NAME + "." + Categories._ID);
                qb.setProjectionMap(sNotesProjectionMap);
                qb.appendWhere(
                    NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID +    // 添加表名前缀以避免歧义
                    "=" +
                    // the position of the note ID itself in the incoming URI
                    uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
                break;

            case LIVE_FOLDER_NOTES:
                // If the incoming URI is from a live folder, chooses the live folder projection.
                qb.setTables(NotePad.Notes.TABLE_NAME);
                qb.setProjectionMap(sLiveFolderProjectionMap);
                break;
                
            // If the incoming URI is for categories
            case CATEGORIES:
                qb.setTables(Categories.TABLE_NAME);
                qb.setProjectionMap(sCategoriesProjectionMap);
                break;
                
            // If the incoming URI is for a single category
            case CATEGORY_ID:
                qb.setTables(Categories.TABLE_NAME);
                qb.setProjectionMap(sCategoriesProjectionMap);
                qb.appendWhere(
                    Categories._ID + "=" + 
                    uri.getPathSegments().get(1));
                break;
                
            // If the incoming URI is for notes by category
            case NOTES_BY_CATEGORY:
                qb.setTables(NotePad.Notes.TABLE_NAME + " LEFT JOIN " + Categories.TABLE_NAME + 
                        " ON " + NotePad.Notes.TABLE_NAME + ".category_id = " + Categories.TABLE_NAME + "." + Categories._ID);
                qb.setProjectionMap(sNotesProjectionMap);
                // 对于URI格式 notes/categories/#/notes，分类ID在索引2位置
                // 对于URI格式 categories/#/notes，分类ID在索引1位置
                int categoryIdIndex = (uri.getPathSegments().size() >= 4 && uri.getPathSegments().get(1).equals("categories")) ? 2 : 1;
                qb.appendWhere(
                    NotePad.Notes.TABLE_NAME + ".category_id=" + 
                    uri.getPathSegments().get(categoryIdIndex));
                break;
                
            // 处理notes/categories URI，与CATEGORIES处理相同
            case NOTES_CATEGORIES:
                qb.setTables(Categories.TABLE_NAME);
                qb.setProjectionMap(sCategoriesProjectionMap);
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        String orderBy;
        // If no sort order is specified, uses the default based on the URI type
        if (TextUtils.isEmpty(sortOrder)) {
            if (match == CATEGORIES || match == CATEGORY_ID || match == NOTES_CATEGORIES) {
                orderBy = Categories.DEFAULT_SORT_ORDER;
            } else {
                orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
            }
        } else {
            // otherwise, uses the incoming sort order
            orderBy = sortOrder;
        }

       // Opens the database object in "read" mode, since no writes need to be done.
       SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       // 修复selection参数中未带表名前缀的_id、title和note列引用
       String modifiedSelection = selection;
       if (selection != null && (match == NOTES || match == NOTE_ID || match == NOTES_BY_CATEGORY)) {
           // 当查询涉及notes表且selection中包含_id列时，添加表名前缀
           // 改进的正则表达式，能够匹配更多形式的_id引用，包括括号内的情况
           modifiedSelection = selection.replaceAll("(\\(|\\s+|^)_id(\\s*=)", "$1" + NotePad.Notes.TABLE_NAME + "._id$2");
           // 额外处理括号内的_id引用形式，如"(_id=2)"
           modifiedSelection = modifiedSelection.replaceAll("\\(_id\\s*=", "(" + NotePad.Notes.TABLE_NAME + "._id = ");
           
           // 处理title列的歧义，包括使用常量形式的引用
           // 处理LOWER(title)形式
           modifiedSelection = modifiedSelection.replaceAll("LOWER\\(title\\)", "LOWER(" + NotePad.Notes.TABLE_NAME + ".title)");
           // 处理LOWER(NotePad.Notes.COLUMN_NAME_TITLE)形式，即LOWER(note)或LOWER(title)的常量引用
           modifiedSelection = modifiedSelection.replaceAll("LOWER\\(" + NotePad.Notes.COLUMN_NAME_TITLE + "\\)", "LOWER(" + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE + ")");
           
           // 处理note列的歧义
           modifiedSelection = modifiedSelection.replaceAll("LOWER\\(note\\)", "LOWER(" + NotePad.Notes.TABLE_NAME + ".note)");
           modifiedSelection = modifiedSelection.replaceAll("LOWER\\(" + NotePad.Notes.COLUMN_NAME_NOTE + "\\)", "LOWER(" + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE + ")");
           
           // 处理其他形式的title引用
           modifiedSelection = modifiedSelection.replaceAll("(\\(|\\s+|^)" + NotePad.Notes.COLUMN_NAME_TITLE + "(\\s*[=<>LIKE])", "$1" + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE + "$2");
           // 处理其他形式的note引用
           modifiedSelection = modifiedSelection.replaceAll("(\\(|\\s+|^)" + NotePad.Notes.COLUMN_NAME_NOTE + "(\\s*[=<>LIKE])", "$1" + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE + "$2");
       }

       /*
        * Performs the query. If no problems occur trying to read the database, then a Cursor
        * object is returned; otherwise, the cursor variable contains null. If no records were
        * selected, then the Cursor object is empty, and Cursor.getCount() returns 0.
        */
       Cursor c = qb.query(
           db,            // The database to query
           projection,    // The columns to return from the query
           modifiedSelection,     // 修改后的where子句，确保_id列有表名前缀
           selectionArgs, // The values for the where clause
           null,          // don't group the rows
           null,          // don't filter by row groups
           orderBy        // The sort order
       );

       // Tells the Cursor what URI to watch, so it knows when its source data changes
       c.setNotificationUri(getContext().getContentResolver(), uri);
       return c;
   }

   /**
     * This is called when a client calls {@link android.content.ContentResolver#getType(Uri)}.
     * Returns the MIME data type of the URI given as a parameter.
     *
     * @param uri The URI whose MIME type is desired.
     * @return The MIME type of the URI.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public String getType(Uri uri) {

        /**
         * Chooses the MIME type based on the incoming URI pattern
         */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for notes or live folders, returns the general content type.
            case NOTES:
            case LIVE_FOLDER_NOTES:
            case NOTES_BY_CATEGORY:
                return NotePad.Notes.CONTENT_TYPE;

            // If the pattern is for note IDs, returns the note ID content type.
            case NOTE_ID:
                return NotePad.Notes.CONTENT_ITEM_TYPE;
                
            // If the pattern is for categories, returns the categories content type.
            case CATEGORIES:
            case NOTES_CATEGORIES:
                return Categories.CONTENT_TYPE;
                
            // If the pattern is for category IDs, returns the category ID content type.
            case CATEGORY_ID:
                return Categories.CONTENT_ITEM_TYPE;

            // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
     }

//BEGIN_INCLUDE(stream)
    /**
     * This describes the MIME types that are supported for opening a note
     * URI as a stream.
     */
    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    /**
     * Returns the types of available data streams.  URIs to specific notes are supported.
     * The application can convert such a note to a plain text stream.
     *
     * @param uri the URI to analyze
     * @param mimeTypeFilter The MIME type to check for. This method only returns a data stream
     * type for MIME types that match the filter. Currently, only text/plain MIME types match.
     * @return a data stream MIME type. Currently, only text/plan is returned.
     * @throws IllegalArgumentException if the URI pattern doesn't match any supported patterns.
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        /**
         *  Chooses the data stream type based on the incoming URI pattern.
         */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for notes or live folders, return null. Data streams are not
            // supported for this type of URI.
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;

            // If the pattern is for note IDs and the MIME filter is text/plain, then return
            // text/plain
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

                // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
    }


    /**
     * Returns a stream of data for each supported stream type. This method does a query on the
     * incoming URI, then uses
     * {@link android.content.ContentProvider#openPipeHelper(Uri, String, Bundle, Object,
     * PipeDataWriter)} to start another thread in which to convert the data into a stream.
     *
     * @param uri The URI pattern that points to the data stream
     * @param mimeTypeFilter A String containing a MIME type. This method tries to get a stream of
     * data with this MIME type.
     * @param opts Additional options supplied by the caller.  Can be interpreted as
     * desired by the content provider.
     * @return AssetFileDescriptor A handle to the file.
     * @throws FileNotFoundException if there is no file associated with the incoming URI.
     */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        // Checks to see if the MIME type filter matches a supported MIME type.
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        // If the MIME type is supported
        if (mimeTypes != null) {

            // Retrieves the note for this URI. Uses the query method defined for this provider,
            // rather than using the database query method.
            Cursor c = query(
                    uri,                    // The URI of a note
                    READ_NOTE_PROJECTION,   // Gets a projection containing the note's ID, title,
                                            // and contents
                    null,                   // No WHERE clause, get all matching records
                    null,                   // Since there is no WHERE clause, no selection criteria
                    null                    // Use the default sort order (modification date,
                                            // descending
            );


            // If the query fails or the cursor is empty, stop
            if (c == null || !c.moveToFirst()) {

                // If the cursor is empty, simply close the cursor and return
                if (c != null) {
                    c.close();
                }

                // If the cursor is null, throw an exception
                throw new FileNotFoundException("Unable to query " + uri);
            }

            // Start a new thread that pipes the stream data back to the caller.
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // If the MIME type is not supported, return a read-only handle to the file.
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    /**
     * Implementation of {@link android.content.ContentProvider.PipeDataWriter}
     * to perform the actual work of converting the data in one of cursors to a
     * stream of data for the client to read.
     */
    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
            Bundle opts, Cursor c) {
        // We currently only support conversion-to-text from a single note entry,
        // so no need for cursor data type checking here.
        // 使用ParcelFileDescriptor.AutoCloseOutputStream替代直接使用FileOutputStream
        // 这是Android Q及更高版本推荐的方式，可以避免ashmem pinning警告
        ParcelFileDescriptor.AutoCloseOutputStream fout = new ParcelFileDescriptor.AutoCloseOutputStream(output);
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
                pw.close();
            }
            // AutoCloseOutputStream会在PrintWriter.close()时自动关闭，无需手动关闭
        }
    }
//END_INCLUDE(stream)

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#insert(Uri, ContentValues)}.
     * Inserts a new row into the database. This method sets up default values for any
     * columns that are not included in the incoming map.
     * If rows were inserted, then listeners are notified of the change.
     * @return The row ID of the inserted row.
     * @throws SQLException if the insertion fails.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        // Validates the incoming URI. Only the full provider URI is allowed for inserts.
        int match = sUriMatcher.match(uri);
        if (match != NOTES && match != CATEGORIES && match != NOTES_CATEGORIES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // A map to hold the new record's values.
        ContentValues values;

        // If the incoming values map is not null, uses it for the new values.
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            // Otherwise, create a new value map
            values = new ContentValues();
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Uri insertUri;
        long rowId;

        // Performs the insert based on the incoming URI pattern
        if (match == NOTES) {
            // Gets the current system time in milliseconds
            Long now = Long.valueOf(System.currentTimeMillis());

            // If the values map doesn't contain the creation date, sets the value to the current time.
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
                values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
            }

            // If the values map doesn't contain the modification date, sets the value to the current
            // time.
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
                values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
            }

            // If the values map doesn't contain a title, sets the value to the default title.
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
                Resources r = Resources.getSystem();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
            }

            // If the values map doesn't contain note text, sets the value to an empty string.
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
            }

            // If the values map doesn't contain category_id, sets to default category
            if (values.containsKey("category_id") == false) {
                values.put("category_id", Categories.DEFAULT_CATEGORY_ID);
            }

            // Performs the insert and returns the ID of the new note.
            rowId = db.insert(
                NotePad.Notes.TABLE_NAME,        // The table to insert into.
                NotePad.Notes.COLUMN_NAME_NOTE,  // A hack, SQLite sets this column value to null
                                                 // if values is empty.
                values                           // A map of column names, and the values to insert
                                                 // into the columns.
            );

            // If the insert succeeded, the row ID exists.
            if (rowId > 0) {
                // Creates a URI with the note ID pattern and the new row ID appended to it.
                insertUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);

                // Notifies observers registered against this provider that the data changed.
                getContext().getContentResolver().notifyChange(insertUri, null);
                // Also notify the entire notes data set to ensure the main screen refreshes
                getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
                return insertUri;
            }
        } else if (match == CATEGORIES || match == NOTES_CATEGORIES) {
            // Category insert
            if (values.containsKey(Categories.COLUMN_NAME_TITLE) == false) {
                throw new SQLException("Category title is required");
            }

            // Performs the insert and returns the ID of the new category.
            rowId = db.insert(
                Categories.TABLE_NAME,        // The table to insert into.
                null,                         // Null column hack
                values                        // The values to insert
            );

            // If the insert succeeded, the row ID exists.
            if (rowId > 0) {
                // Creates a URI with the category ID appended
                insertUri = ContentUris.withAppendedId(Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"), rowId);

                // Notifies observers registered against this provider that the data changed.
                getContext().getContentResolver().notifyChange(insertUri, null);
                // Also notify the entire categories data set and notes data set to ensure the main screen refreshes
                getContext().getContentResolver().notifyChange(Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"), null);
                getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
                return insertUri;
            }
        }

        // If the insert didn't succeed, throws an exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#delete(Uri, String, String[])}.
     * Deletes records from the database. If the incoming URI matches the note ID URI pattern,
     * this method deletes the one record specified by the ID in the URI. Otherwise, it deletes a
     * a set of records. The record or records must also match the input selection criteria
     * specified by where and whereArgs.
     *
     * If rows were deleted, then listeners are notified of the change.
     * @return If a "where" clause is used, the number of rows affected is returned, otherwise
     * 0 is returned. To delete all rows and get a row count, use "1" as the where clause.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // Does the delete based on the incoming URI pattern.
        switch (sUriMatcher.match(uri)) {

            // If the incoming pattern matches the general pattern for notes, does a delete
            // based on the incoming "where" columns and arguments.
            case NOTES:
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,  // The database table name
                    where,                     // The incoming where clause column names
                    whereArgs                  // The incoming where clause values
                );
                break;

                // If the incoming URI matches a single note ID, does the delete based on the
                // incoming data, but modifies the where clause to restrict it to the
                // particular note ID.
            case NOTE_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the
                 * desired note ID.
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Performs the delete.
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,  // The database table name.
                    finalWhere,                // The final WHERE clause
                    whereArgs                  // The incoming where clause values.
                );
                break;
                
                // If the incoming pattern matches the general pattern for categories
            case CATEGORIES:
            case NOTES_CATEGORIES:
                // Prevent deleting all categories including default
                throw new IllegalArgumentException("Cannot delete all categories");
                
                // If the incoming URI matches a single category ID
            case CATEGORY_ID:
                String categoryId = uri.getPathSegments().get(1);
                
                // Prevent deleting default category
                if (Long.parseLong(categoryId) == Categories.DEFAULT_CATEGORY_ID) {
                    throw new IllegalArgumentException("Cannot delete default category");
                }
                
                finalWhere = Categories._ID + " = " + categoryId;
                
                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                
                // Move notes from this category to default category before deleting
                ContentValues values = new ContentValues();
                values.put("category_id", Categories.DEFAULT_CATEGORY_ID);
                db.update(NotePad.Notes.TABLE_NAME, values, "category_id = " + categoryId, null);
                
                // Performs the delete.
                count = db.delete(
                    Categories.TABLE_NAME,    // The database table name.
                    finalWhere,               // The final WHERE clause
                    whereArgs                 // The incoming where clause values.
                );
                break;
                
                // If the incoming URI matches notes by category
            case NOTES_BY_CATEGORY:
                String categoryIdForNotes = uri.getPathSegments().get(1);
                
                finalWhere = "category_id = " + categoryIdForNotes;
                
                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                
                // Performs the delete.
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,  // The database table name.
                    finalWhere,                // The final WHERE clause
                    whereArgs                  // The incoming where clause values.
                );
                break;

                // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);
        
        // Also notify the entire notes data set to ensure the main screen refreshes
        if (uri.toString().contains(NotePad.Notes.CONTENT_URI.toString())) {
            getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
        }
        
        // If it's a category operation, also notify the categories data set
        if (uri.toString().contains("categories")) {
            getContext().getContentResolver().notifyChange(Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"), null);
        }

        // Returns the number of rows deleted.
        return count;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#update(Uri,ContentValues,String,String[])}
     * Updates records in the database. The column names specified by the keys in the values map
     * are updated with new data specified by the values in the map. If the incoming URI matches the
     * note ID URI pattern, then the method updates the one record specified by the ID in the URI;
     * otherwise, it updates a set of records. The record or records must match the input
     * selection criteria specified by where and whereArgs.
     * If rows were updated, then listeners are notified of the change.
     *
     * @param uri The URI pattern to match and update.
     * @param values A map of column names (keys) and new values (values).
     * @param where An SQL "WHERE" clause that selects records based on their column values. If this
     * is null, then all records that match the URI pattern are selected.
     * @param whereArgs An array of selection criteria. If the "where" param contains value
     * placeholders ("?"), then each placeholder is replaced by the corresponding element in the
     * array.
     * @return The number of rows updated.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;
        int match = sUriMatcher.match(uri);

        // If updating a note, ensure modification date is updated
        if (match == NOTES || match == NOTE_ID) {
            if (values != null) {
                values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, Long.valueOf(System.currentTimeMillis()));
            }
        }

        // Does the update based on the incoming URI pattern
        switch (match) {

            // If the incoming URI matches the general notes pattern, does the update based on
            // the incoming data.
            case NOTES:
                // Does the update and returns the number of rows updated.
                count = db.update(
                    NotePad.Notes.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    where,                    // The where clause column names.
                    whereArgs                 // The where clause column values to select on.
                );
                break;

            // If the incoming URI matches a single note ID, does the update based on the incoming
            // data, but modifies the where clause to restrict it to the particular note ID.
            case NOTE_ID:
                // From the incoming URI, get the note ID
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);

                /*
                 * Starts creating the final WHERE clause by restricting it to the incoming
                 * note ID.
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final WHERE
                // clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Does the update and returns the number of rows updated.
                count = db.update(
                    NotePad.Notes.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    finalWhere,               // The final WHERE clause to use
                                              // placeholders for whereArgs
                    whereArgs                 // The where clause column values to select on, or
                                              // null if the values are in the where argument.
                );
                break;
                
            // If the incoming URI matches the categories pattern
            case CATEGORIES:
            case NOTES_CATEGORIES:
                count = db.update(
                    Categories.TABLE_NAME,   // The database table name.
                    values,                  // A map of column names and new values to use.
                    where,                   // The where clause column names.
                    whereArgs                // The where clause column values to select on.
                );
                break;
                
            // If the incoming URI matches a single category ID
            case CATEGORY_ID:
                // From the incoming URI, get the category ID
                String categoryId = uri.getPathSegments().get(1);
                
                // Don't allow updating default category
                if (Long.parseLong(categoryId) == Categories.DEFAULT_CATEGORY_ID && 
                    values.containsKey(Categories.COLUMN_NAME_TITLE)) {
                    throw new IllegalArgumentException("Cannot change default category");
                }
                
                finalWhere = Categories._ID + " = " + categoryId;
                
                // If there were additional selection criteria, append them to the final WHERE
                // clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                
                count = db.update(
                    Categories.TABLE_NAME, // The database table name.
                    values,               // A map of column names and new values to use.
                    finalWhere,           // The final WHERE clause to use
                    whereArgs             // The where clause column values to select on
                );
                break;
                
            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);
        
        // Also notify the entire notes data set to ensure the main screen refreshes
        if (uri.toString().contains(NotePad.Notes.CONTENT_URI.toString())) {
            getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
        }
        
        // If it's a category operation, also notify the categories data set
        if (uri.toString().contains("categories")) {
            getContext().getContentResolver().notifyChange(Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"), null);
        }

        // Returns the number of rows updated.
        return count;
    }

    /**
     * A test package can call this to get a handle to the database underlying NotePadProvider,
     * so it can insert test data into the database. The test case class is responsible for
     * instantiating the provider in a test context during test setup.
     *
     * @return a handle to the database helper object for the provider's data.
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
