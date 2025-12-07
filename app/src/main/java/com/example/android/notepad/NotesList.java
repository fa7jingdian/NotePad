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

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import android.view.LayoutInflater;
import android.widget.CursorAdapter;
import android.widget.BaseAdapter;

import com.example.android.notepad.NotePad;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SimpleCursorAdapter;
import android.widget.SearchView;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;
import android.text.InputType;
import android.text.TextUtils;
import com.example.android.notepad.ExportManager;
import com.example.android.notepad.NotePadProvider;
import com.example.android.notepad.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends Activity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE,// 使用标准的title列名，由projection map确保指向正确的表
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
            NotePad.Notes.COLUMN_NAME_CREATE_DATE, // 3
            "category_id", // 4
            "category_title" // 5
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;
// UI组件
    private ListView mListView;
    private View mBtnAddNote;
    private View mBtnSort;
    private View mBtnSearch;
    private View mBtnMore;
    private Button mBtnDeleteSelected;
    
    // 批量选择模式标志
    private boolean mMultiSelectMode = false;
    // 选中的笔记ID集合
    private Set<Long> mSelectedNoteIds = new HashSet<>();
    
    // 笔记列表适配器
    private BaseAdapter mAdapter;

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置主题 - 必须在super.onCreate()之前调用
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);

        // 使用自定义布局
        setContentView(R.layout.notes_list);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        // 初始化ListView和空列表提示
        mListView = findViewById(R.id.note_list);
        TextView emptyTextView = findViewById(R.id.empty_notes);
        
        // 添加空值检查，防止崩溃
        if (mListView != null) {
            mListView.setEmptyView(emptyTextView);
            
            /*
             * Sets the callback for context menu activation for the ListView. The listener is set
             * to be this Activity. The effect is that context menus are enabled for items in the
             * ListView, and the context menu is handled by a method in NotesList.
             */
            mListView.setOnCreateContextMenuListener(this);
        } else {
            Log.e(TAG, "ListView is null! findViewById(R.id.note_list) returned null.");
        }

        /* Performs a query to get the notes. Using getContentResolver() directly instead of
         * managedQuery (which is deprecated) to have better control over cursor lifecycle.
         *
         * Please see the introductory note about performing provider operations on the UI thread.
         */
        Cursor cursor = getContentResolver().query(
            getIntent().getData(),            // Use the default content URI for the provider.
            PROJECTION,                       // Return the note ID and title for each note.
            null,                             // No where clause, return all records.
            null,                             // No where clause, therefore no where column values.
            "category_title ASC, " + NotePad.Notes.DEFAULT_SORT_ORDER  // 先按分类标题排序，再按默认排序
        );

        /*
         * The following two arrays create a "map" between columns in the cursor and view IDs
         * for items in the ListView. Each element in the dataColumns array represents
         * a column name; each element in the viewID array represents the ID of a View.
         * The SimpleCursorAdapter maps them in ascending order to determine where each column
         * value will appear in the ListView.
         */

        // 创建自定义的BaseAdapter来实现分组列表
        mAdapter = new NotesAdapter(cursor);
        
        // 设置点击事件监听器
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 分组标题没有ID（返回-1），所以跳过点击事件
                if (id == -1) {
                    return;
                }
                
                // Constructs a new URI from the incoming URI and the row ID
                Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
                // Gets the action from the incoming Intent
                String action = getIntent().getAction();
                // Handles requests for note data
                if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                    // Sets the result to return to the component that called this Activity
                    setResult(RESULT_OK, new Intent().setData(uri));
                } else {
                    // Sends out an Intent to start an Activity that can handle ACTION_EDIT
                    startActivity(new Intent(Intent.ACTION_EDIT, uri));
                }
            }
        });
        // Sets the ListView's adapter to be the base adapter that was just created.
        mListView.setAdapter(mAdapter);

        // 初始化工具栏按钮
        mBtnAddNote = findViewById(R.id.btn_add_note);
        mBtnSort = findViewById(R.id.btn_sort);
        mBtnSearch = findViewById(R.id.btn_search);
        mBtnMore = findViewById(R.id.btn_more);
        // 初始化底部删除按钮
        mBtnDeleteSelected = findViewById(R.id.btn_delete_selected);
        
        // 设置底部删除按钮点击事件
        mBtnDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performBatchDelete();
            }
        });

        // 设置按钮点击事件
        mBtnAddNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 新建笔记
                startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            }
        });

        mBtnSort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 进入分类管理界面
                startActivity(new Intent(NotesList.this, CategoryListActivity.class));
            }
        });

        mBtnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 显示搜索对话框
                showSearchDialog();
            }
        });

        mBtnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 显示设置菜单
                showSettingsMenu(v);
            }
        });
    }

    /**
     * 显示设置菜单
     */
    private void showSettingsMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        Menu menu = popup.getMenu();
        
        // 添加切换到深色主题功能
        menu.add(Menu.NONE, R.id.menu_theme_toggle, Menu.NONE,
                ThemeManager.isDarkTheme(this) ? R.string.menu_theme_light : R.string.menu_theme_dark);
        
        // 添加导出文件功能
        menu.add(Menu.NONE, R.id.menu_export_all, Menu.NONE, R.string.menu_export_all);
        menu.add(Menu.NONE, R.id.menu_export_category, Menu.NONE, R.string.menu_export_category);
        
        // 添加批量删除功能
        menu.add(Menu.NONE, R.id.menu_batch_delete, Menu.NONE, R.string.menu_batch_delete);
        
        // 设置菜单点击监听器
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_theme_toggle) {
                    // 切换主题
                    ThemeManager.toggleTheme(NotesList.this);
                    // 重新创建Activity以应用新主题
                    recreate();
                    return true;
                } else if (itemId == R.id.menu_batch_delete) {
                    // 批量删除
                    toggleMultiSelectMode();
                    return true;
                } else if (itemId == R.id.menu_export_all) {
                    // 导出所有笔记
                    ExportManager exportManager = new ExportManager(NotesList.this);
                    exportManager.exportAllNotes();
                    return true;
                } else if (itemId == R.id.menu_export_category) {
                    // 按分类导出笔记
                    showCategorySelectionDialog();
                    return true;
                }
                return false;
            }
        });
        
        popup.show();
    }

    /**
     * 显示搜索对话框
     */
    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("搜索笔记");

        // 创建搜索输入框
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入搜索内容");
        builder.setView(input);

        // 设置对话框按钮
        builder.setPositiveButton("搜索", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String query = input.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        return true;
    }

    private void performSearch(String query) {
        // 优化搜索逻辑，使用更友好的搜索方式
        String searchQuery = query.trim().toLowerCase();
        
        // 使用更健壮的查询语句，支持大小写不敏感搜索，匹配标题、内容和分类
        // 确保只返回有笔记的记录，避免只显示分类而没有笔记的情况
        String selection = "(LOWER(" + NotePad.Notes.COLUMN_NAME_TITLE + ") LIKE ? OR LOWER(" 
                + NotePad.Notes.COLUMN_NAME_NOTE + ") LIKE ? OR LOWER(category_title) LIKE ?) AND " 
                + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID + " IS NOT NULL";
        String[] selectionArgs = { "%" + searchQuery + "%", "%" + searchQuery + "%", "%" + searchQuery + "%" };

        // 使用与列表显示相同的URI，确保搜索结果与主列表的上下文一致
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                "category_title ASC, " + NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 检查适配器是否存在
        if (mAdapter != null && mAdapter instanceof NotesAdapter) {
            NotesAdapter notesAdapter = (NotesAdapter) mAdapter;
            notesAdapter.changeCursor(cursor);
            
            // 如果搜索结果为空，可以显示一个提示
            if (cursor.getCount() == 0) {
                Toast.makeText(NotesList.this, "没有找到匹配的笔记", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * 重置搜索结果，显示所有笔记
     */
    private void resetSearchResults() {
        // 使用refreshNoteList方法重置搜索结果，确保URI和排序方式一致
        refreshNoteList();
    }
    
    /**
     * 刷新笔记列表，使用当前Intent的URI和统一的排序方式
     */
    private void refreshNoteList() {
        // 检查适配器是否存在
        if (mAdapter == null) return;
        
        // 使用getContentResolver直接查询最新的笔记数据
        Cursor newCursor = getContentResolver().query(
                getIntent().getData(), // 完整的URI，包括附加的限制参数
                PROJECTION,            // 返回的列
                null,                  // 没有WHERE子句，返回所有行
                null,                  // 没有WHERE参数
                "category_title ASC, " + NotePad.Notes.DEFAULT_SORT_ORDER // 先按分类标题升序，再按修改日期降序排序
        );
        
        // 使用新的Cursor更新适配器
        ((NotesAdapter) mAdapter).changeCursor(newCursor);
    }
    
    // 笔记列表适配器（命名内部类）
    private class NotesAdapter extends BaseAdapter {
        // 定义两种视图类型：分组标题和笔记项
        private static final int TYPE_GROUP_HEADER = 0;
        private static final int TYPE_NOTE_ITEM = 1;
        private static final int TYPE_COUNT = 2;
        
        // 存储所有列表项的数据（包括分组标题和笔记）
        private List<ItemData> mItemDataList = new ArrayList<>();
        
        // 内部类：列表项数据
        private class ItemData {
            boolean isGroupHeader; // 是否是分组标题
            String categoryTitle;  // 分类标题（如果是分组标题）
            long noteId;          // 笔记ID（如果是笔记项）
            String title;         // 笔记标题
            String timestamp;     // 笔记时间戳
        }
        
        // 构造方法
        public NotesAdapter(Cursor cursor) {
            updateItemDataList(cursor);
        }
        
        // 更新列表项数据
        private void updateItemDataList(Cursor cursor) {
            mItemDataList.clear();
            if (cursor == null || cursor.getCount() == 0) {
                return;
            }
            
            // 保存原始位置
            int originalPosition = cursor.getPosition();
            
            try {
                // 使用HashSet来存储已经处理过的笔记ID，避免重复添加
                Set<Long> processedNoteIds = new HashSet<>();
                
                // 移动到第一个位置
                cursor.moveToFirst();
                String currentCategory = getCategoryTitle(cursor);
                
                // 添加第一个分组标题
                ItemData groupHeader = new ItemData();
                groupHeader.isGroupHeader = true;
                groupHeader.categoryTitle = currentCategory;
                mItemDataList.add(groupHeader);
                
                // 添加第一个笔记项
                long noteId = cursor.getLong(0);
                if (!processedNoteIds.contains(noteId)) {
                    ItemData noteItem = new ItemData();
                    noteItem.isGroupHeader = false;
                    noteItem.noteId = noteId;
                    noteItem.title = cursor.getString(1); // 第1列是标题
                    noteItem.timestamp = cursor.getString(2); // 第2列是修改日期
                    mItemDataList.add(noteItem);
                    processedNoteIds.add(noteId);
                }
                
                // 遍历所有笔记，添加分组标题和笔记项
                while (cursor.moveToNext()) {
                    String category = getCategoryTitle(cursor);
                    if (!TextUtils.equals(category, currentCategory)) {
                        // 添加新的分组标题
                        groupHeader = new ItemData();
                        groupHeader.isGroupHeader = true;
                        groupHeader.categoryTitle = category;
                        mItemDataList.add(groupHeader);
                        
                        currentCategory = category;
                    }
                    
                    // 添加笔记项
                    noteId = cursor.getLong(0);
                    if (!processedNoteIds.contains(noteId)) {
                        ItemData noteItem = new ItemData();
                        noteItem.isGroupHeader = false;
                        noteItem.noteId = noteId;
                        noteItem.title = cursor.getString(1); // 第1列是标题
                        noteItem.timestamp = cursor.getString(2); // 第2列是修改日期
                        mItemDataList.add(noteItem);
                        processedNoteIds.add(noteId);
                    }
                }
            } finally {
                // 恢复原始位置
                cursor.moveToPosition(originalPosition);
            }
        }
        
        // 获取分类标题
        private String getCategoryTitle(Cursor cursor) {
            String categoryTitle = cursor.getString(5); // 第5列是category_title
            return categoryTitle != null && !categoryTitle.isEmpty() ? categoryTitle : "未分类";
        }
        
        public int getViewTypeCount() {
            return TYPE_COUNT;
        }
        
        public int getItemViewType(int position) {
            return mItemDataList.get(position).isGroupHeader ? TYPE_GROUP_HEADER : TYPE_NOTE_ITEM;
        }
        
        public int getCount() {
            return mItemDataList.size();
        }
        
        public Object getItem(int position) {
            return mItemDataList.get(position);
        }
        
        public long getItemId(int position) {
            ItemData itemData = mItemDataList.get(position);
            if (itemData.isGroupHeader) {
                return -1; // 分组标题没有ID
            } else {
                return itemData.noteId;
            }
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemData itemData = mItemDataList.get(position);
            
            if (itemData.isGroupHeader) {
                // 分组标题视图
                View view;
                if (convertView == null || getItemViewType(position) != TYPE_GROUP_HEADER) {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.noteslist_group_item, parent, false);
                } else {
                    view = convertView;
                }
                
                // 绑定分组标题
                TextView groupTitle = view.findViewById(R.id.group_title);
                groupTitle.setText(itemData.categoryTitle);
                
                return view;
            } else {
                // 笔记项视图
                View view;
                if (convertView == null || getItemViewType(position) != TYPE_NOTE_ITEM) {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.noteslist_item, parent, false);
                } else {
                    view = convertView;
                }
                
                // 绑定笔记项
                // 笔记标题
                TextView titleView = view.findViewById(R.id.note_title);
                titleView.setText(itemData.title); // 直接从ItemData获取标题
                
                // 时间戳
                TextView timestampView = view.findViewById(R.id.timestamp);
                String timestamp = itemData.timestamp; // 直接从ItemData获取时间戳
                try {
                    long time = Long.parseLong(timestamp);
                    timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(time));
                } catch (NumberFormatException e) {
                    timestamp = "未知时间";
                }
                timestampView.setText(timestamp);
                
                // 分类标签（隐藏，因为分组标题已经显示了分类）
                TextView categoryLabel = view.findViewById(R.id.category_label);
                if (categoryLabel != null) {
                    categoryLabel.setVisibility(View.GONE);
                }
                
                // 分类间隔（隐藏，因为分组标题已经起到了间隔作用）
                View categoryDivider = view.findViewById(R.id.category_divider);
                if (categoryDivider != null) {
                    categoryDivider.setVisibility(View.GONE);
                }
                
                // 获取笔记ID
                final long noteId = itemData.noteId;
                
                // 为删除图标添加点击事件
                ImageView deleteIcon = view.findViewById(R.id.note_delete);
                if (deleteIcon != null) {
                    deleteIcon.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            // 显示删除确认对话框
                            new AlertDialog.Builder(NotesList.this)
                                    .setTitle("删除笔记")
                                    .setMessage("确定要删除这条笔记吗？")
                                    .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // 执行删除操作
                                            getContentResolver().delete(
                                                    ContentUris.withAppendedId(getIntent().getData(), noteId),
                                                    null,
                                                    null
                                            );
                                            
                                            // 更新列表
                                            refreshNoteList();
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }
                    });
                }
                
                // 处理多选框
                final CheckBox checkBox = view.findViewById(R.id.note_checkbox);
                if (checkBox != null) {
                    // 根据当前模式显示或隐藏多选框
                    checkBox.setVisibility(mMultiSelectMode ? View.VISIBLE : View.GONE);
                    
                    // 设置多选框的选中状态
                    checkBox.setChecked(mSelectedNoteIds.contains(noteId));
                    
                    // 添加点击事件监听器
                    checkBox.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            if (checkBox.isChecked()) {
                                mSelectedNoteIds.add(noteId);
                            } else {
                                mSelectedNoteIds.remove(noteId);
                            }
                            // 更新底部删除按钮的标题
                            updateDeleteButtonTitle();
                        }
                    });
                    
                    // 为整个列表项添加点击事件，用于在批量选择模式下切换选中状态
                    view.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            if (mMultiSelectMode) {
                                checkBox.setChecked(!checkBox.isChecked());
                                if (checkBox.isChecked()) {
                                    mSelectedNoteIds.add(noteId);
                                } else {
                                    mSelectedNoteIds.remove(noteId);
                                }
                                // 更新底部删除按钮的标题
                                updateDeleteButtonTitle();
                            } else {
                                // 非批量选择模式下，默认点击行为（打开笔记）
                                Intent intent = new Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(getIntent().getData(), noteId));
                                startActivity(intent);
                            }
                        }
                    });
                }
                
                return view;
            }
        }
        
        // 更新Cursor并刷新数据
        public void changeCursor(Cursor newCursor) {
            updateItemDataList(newCursor);
            notifyDataSetChanged();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager) 
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);
        MenuItem mBatchDeleteItem = menu.findItem(R.id.menu_batch_delete);
        
        // 先移除之前可能添加的删除按钮
        menu.removeItem(R.id.menu_batch_delete_button);

        // 如果在批量选择模式下
        if (mMultiSelectMode) {
            // 隐藏批量删除菜单项
            if (mBatchDeleteItem != null) {
                mBatchDeleteItem.setVisible(false);
            }
            
            // 直接添加删除按钮
            MenuItem deleteButtonItem = menu.add(
                    Menu.NONE, 
                    R.id.menu_batch_delete_button, 
                    Menu.NONE, 
                    "删除选中项 (" + mSelectedNoteIds.size() + ")"
            );
            deleteButtonItem.setIcon(R.drawable.ic_menu_delete);
            deleteButtonItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            // 显示批量删除菜单项
            if (mBatchDeleteItem != null) {
                mBatchDeleteItem.setVisible(true);
            }
        }

        // If the clipboard contains an item, enables the Paste option on the menu.
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // If the clipboard is empty, disables the menu's Paste option.
            mPasteItem.setEnabled(false);
        }

        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     *
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            /*
             * Launches a new Activity using an Intent. The intent filter for the Activity
             * has to have action ACTION_INSERT. No category is set, so DEFAULT is assumed.
             * In effect, this starts the NoteEditor Activity in NotePad.
             */
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_paste) {
            /*
             * Launches a new Activity using an Intent. The intent filter for the Activity
             * has to have action ACTION_PASTE. No category is set, so DEFAULT is assumed.
             * In effect, this starts the NoteEditor Activity in NotePad.
             */
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_batch_delete) {
            /*
             * Toggle multi-select mode for batch deletion
             */
            toggleMultiSelectMode();
            return true;
        } else if (item.getItemId() == R.id.menu_batch_delete_button) {
            /*
             * Perform batch delete of selected notes
             */
            performBatchDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */


    /**
     * 显示分类选择对话框，用于选择要导出的分类
     */
    private void showCategorySelectionDialog() {
        // 查询所有分类
        final Cursor cursor = managedQuery(
                Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                new String[] { NotePadProvider.Categories._ID, NotePadProvider.Categories.COLUMN_NAME_TITLE },
                null,
                null,
                NotePadProvider.Categories.DEFAULT_SORT_ORDER
        );

        if (cursor != null && cursor.getCount() > 0) {
            // 创建分类名称数组
            final String[] categoryNames = new String[cursor.getCount()];
            final long[] categoryIds = new long[cursor.getCount()];
            int i = 0;
            while (cursor.moveToNext()) {
                categoryIds[i] = cursor.getLong(0);
                categoryNames[i] = cursor.getString(1);
                i++;
            }

            // 创建AlertDialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.export_select_category)
                    .setItems(categoryNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 导出所选分类的笔记
                            ExportManager exportManager = new ExportManager(NotesList.this);
                            exportManager.exportNotesByCategory(categoryIds[which]);
                        }
                    });
            builder.create().show();
        } else {
            Toast.makeText(this, R.string.export_error_no_notes, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) mListView.getAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // 删除成功后立即更新列表，避免使用已关闭的Cursor
            // 获取当前的适配器
            CursorAdapter adapter = (CursorAdapter) mListView.getAdapter();
            // 获取当前的Cursor并关闭它
            Cursor currentCursor = adapter.getCursor();
            if (currentCursor != null && !currentCursor.isClosed()) {
                currentCursor.close();
            }
            // 使用getContentResolver直接查询新的Cursor，而不是managedQuery
            Cursor newCursor = getContentResolver().query(
                    getIntent().getData(), // 完整的URI，包括附加的限制参数
                    PROJECTION,            // 返回的列
                    null,                  // 没有WHERE子句，返回所有行
                    null,                  // 没有WHERE参数
                    "category_title ASC, " + NotePad.Notes.DEFAULT_SORT_ORDER // 先按分类标题升序，再按修改日期降序排序
            );
            // 使用新的Cursor更新适配器
            adapter.changeCursor(newCursor);

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }
    

    
    /**
     * 切换批量选择模式
     */
    private void toggleMultiSelectMode() {
        mMultiSelectMode = !mMultiSelectMode;
        
        if (mMultiSelectMode) {
            // 进入批量选择模式
            mSelectedNoteIds.clear();
            // 显示批量操作按钮
            showBatchActionButtons();
        } else {
            // 退出批量选择模式
            mSelectedNoteIds.clear();
            // 隐藏批量操作按钮
            hideBatchActionButtons();
        }
        
        // 刷新菜单，显示/隐藏删除按钮
        invalidateOptionsMenu();
        
        // 刷新列表，更新多选框的显示状态
        mListView.invalidateViews();
    }
    
    /**
     * 显示批量操作按钮
     */
    private void showBatchActionButtons() {
        // 显示底部删除按钮
        if (mBtnDeleteSelected != null) {
            mBtnDeleteSelected.setVisibility(View.VISIBLE);
            updateDeleteButtonTitle();
        }
    }
    
    /**
     * 隐藏批量操作按钮
     */
    private void hideBatchActionButtons() {
        // 隐藏底部删除按钮
        if (mBtnDeleteSelected != null) {
            mBtnDeleteSelected.setVisibility(View.GONE);
        }
    }
    
    /**
     * 更新底部删除按钮的标题，显示当前选中的笔记数量
     */
    private void updateDeleteButtonTitle() {
        if (mBtnDeleteSelected != null) {
            mBtnDeleteSelected.setText("删除选中项 (" + mSelectedNoteIds.size() + ")");
        }
    }
    
    /**
     * 执行批量删除操作
     */
    private void performBatchDelete() {
        if (mSelectedNoteIds.isEmpty()) {
            // 如果没有选中任何笔记，显示提示
            Toast.makeText(this, "请先选择要删除的笔记", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示删除确认对话框
        new AlertDialog.Builder(this)
                .setTitle("批量删除")
                .setMessage("确定要删除选中的 " + mSelectedNoteIds.size() + " 条笔记吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 执行批量删除操作
                        for (long noteId : mSelectedNoteIds) {
                            getContentResolver().delete(
                                    ContentUris.withAppendedId(getIntent().getData(), noteId),
                                    null,
                                    null
                            );
                        }
                        
                        // 更新列表
                        refreshNoteList();
                        
                        // 退出批量选择模式
                        toggleMultiSelectMode();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }



    // ContentObserver用于监听数据变化
    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // 数据发生变化时刷新列表
            refreshNoteList();
        }
    };

    /**
     * 当Activity从后台回到前台时调用
     * 重新查询数据并更新适配器，确保ListView显示最新的数据
     */
    @Override
    protected void onResume() {
        super.onResume();
        
        // 注册ContentObserver监听数据变化
        getContentResolver().registerContentObserver(
                NotePad.Notes.CONTENT_URI, 
                true, // 监听所有子URI
                mContentObserver);

        // 刷新笔记列表
        refreshNoteList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 取消注册ContentObserver
        getContentResolver().unregisterContentObserver(mContentObserver);
    }
    
    /**
     * 当Activity销毁时调用
     * 关闭Cursor以避免资源泄漏
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // NotesAdapter继承自BaseAdapter，不是CursorAdapter，不需要处理Cursor
        // 由于NotesAdapter内部已经将数据转换为ItemData列表，不需要关闭Cursor
        // 清理适配器引用
        mListView.setAdapter(null);
    }
}
