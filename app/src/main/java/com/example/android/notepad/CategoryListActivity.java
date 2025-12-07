package com.example.android.notepad;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.content.ContentValues;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.example.android.notepad.NotePadProvider;

/**
 * 分类列表Activity，用于管理笔记分类
 */
public class CategoryListActivity extends Activity {

    // 用于查询的列
    private static final String[] PROJECTION = new String[] {
            NotePadProvider.Categories._ID,          // 0
            NotePadProvider.Categories.COLUMN_NAME_TITLE, // 1
    };

    // 适配器绑定的数据列
    private static final String[] DATA_COLUMNS = new String[] {
            NotePadProvider.Categories.COLUMN_NAME_TITLE
    };

    // 适配器绑定的视图ID
    private static final int[] VIEW_IDS = new int[] {
            R.id.text1
    };

    // 菜单ID常量
    private static final int MENU_ITEM_CREATE = 1;
    private static final int MENU_ITEM_EDIT = 2;
    private static final int MENU_ITEM_DELETE = 3;
    private static final int MENU_ITEM_THEME_TOGGLE = 4;

    private ContentResolver mContentResolver;
    private SimpleCursorAdapter mAdapter;
    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置主题 - 必须在super.onCreate()之前调用
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);

        // 设置视图
        setContentView(R.layout.category_list);

        // 设置标题
        setTitle(R.string.category_list_title);

        mContentResolver = getContentResolver();

        // 查询分类数据
        Cursor cursor = mContentResolver.query(
                Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                PROJECTION,
                null,
                null,
                NotePadProvider.Categories.DEFAULT_SORT_ORDER
        );

        // 创建适配器
        mAdapter = new SimpleCursorAdapter(
                this,
                R.layout.category_item,
                cursor,
                DATA_COLUMNS,
                VIEW_IDS
        );
        
        // 设置视图绑定器，用于处理删除按钮的可见性和点击事件
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                // 找到分类ID列
                int idColumnIndex = cursor.getColumnIndex(NotePadProvider.Categories._ID);
                final long categoryId = cursor.getLong(idColumnIndex);
                
                // 获取根视图
                View rootView = (View) view.getParent();
                if (rootView != null) {
                    // 找到删除按钮
                    Button deleteButton = rootView.findViewById(R.id.btn_delete);
                    
                    // 非默认分类显示删除按钮
                    if (categoryId != NotePadProvider.Categories.DEFAULT_CATEGORY_ID) {
                        deleteButton.setVisibility(View.VISIBLE);
                        // 设置删除按钮点击事件
                        deleteButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // 实现删除功能
                                deleteCategory(categoryId);
                            }
                        });
                    } else {
                        deleteButton.setVisibility(View.GONE);
                    }
                }
                return false; // 返回false表示默认处理文本绑定
            }
        });

        // 初始化ListView和空列表提示
        mListView = findViewById(R.id.category_list);
        TextView emptyTextView = findViewById(R.id.empty_categories);
        mListView.setEmptyView(emptyTextView);
        // 设置适配器
        mListView.setAdapter(mAdapter);

        // 注册上下文菜单
        registerForContextMenu(mListView);

        // 初始化完成

        // 设置列表项点击事件
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 查看该分类下的笔记
                Uri categoryUri = ContentUris.withAppendedId(
                        Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                        id
                );
                
                // 跳转到笔记列表，并显示该分类下的笔记
                Intent intent = new Intent(CategoryListActivity.this, NotesList.class);
                intent.setData(Uri.withAppendedPath(categoryUri, "notes"));
                startActivity(intent);
            }
        });
        
        // 设置新增分类按钮的点击事件
        Button addCategoryButton = findViewById(R.id.btn_add_category);
        addCategoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 调用创建分类的方法
                createCategory();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 创建菜单项
        menu.add(Menu.NONE, MENU_ITEM_CREATE, Menu.NONE, R.string.menu_create_category);

        // 添加主题切换菜单项
        menu.add(Menu.NONE, MENU_ITEM_THEME_TOGGLE, Menu.NONE,
                ThemeManager.isDarkTheme(this) ? R.string.menu_theme_light : R.string.menu_theme_dark);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_CREATE:
                // 创建新分类
                createCategory();
                return true;
            case MENU_ITEM_THEME_TOGGLE:
                // 切换主题
                ThemeManager.toggleTheme(this);
                recreate();
                return true;
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Cursor cursor = (Cursor) mListView.getAdapter().getItem(info.position);
        
        // 获取分类ID
                int idColumnIndex = cursor.getColumnIndex(NotePadProvider.Categories._ID);
                long categoryId = (idColumnIndex != -1) ? cursor.getLong(idColumnIndex) : 0;
        
        // 为上下文菜单添加选项
        menu.add(Menu.NONE, MENU_ITEM_EDIT, Menu.NONE, R.string.resolve_edit);
        
        // 默认分类不允许删除
        if (categoryId != NotePadProvider.Categories.DEFAULT_CATEGORY_ID) {
            menu.add(Menu.NONE, MENU_ITEM_DELETE, Menu.NONE, R.string.menu_delete);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        long categoryId = info.id;

        switch (item.getItemId()) {
            case MENU_ITEM_EDIT:
                // 编辑分类
                editCategory(categoryId);
                return true;
            case MENU_ITEM_DELETE:
                // 删除分类
                deleteCategory(categoryId);
                return true;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新加载数据
        reloadData();
    }

    /**
     * 重新加载分类数据
     */
    private void reloadData() {
        Cursor cursor = mContentResolver.query(
                Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                PROJECTION,
                null,
                null,
                NotePadProvider.Categories.DEFAULT_SORT_ORDER
        );
        mAdapter.changeCursor(cursor);
    }

    /**
     * 创建新分类
     */
    private void createCategory() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("请输入分类名称");
        builder.setTitle(R.string.create_category_title)
                .setMessage(R.string.create_category_message)
                .setView(input)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = input.getText().toString().trim();
                        if (!title.isEmpty()) {
                            // 创建分类
                            ContentValues values = new ContentValues();
                            values.put(NotePadProvider.Categories.COLUMN_NAME_TITLE, title);
                            mContentResolver.insert(
                                    Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                                    values
                            );
                            // 重新加载数据
                            reloadData();
                        } else {
                            Toast.makeText(CategoryListActivity.this, R.string.category_title_required, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 编辑分类
     * @param categoryId 分类ID
     */
    private void editCategory(final long categoryId) {
        // 获取当前分类数据
        Cursor cursor = mContentResolver.query(
                ContentUris.withAppendedId(
                        Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                        categoryId
                ),
                PROJECTION,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            int titleColumnIndex = cursor.getColumnIndex(NotePadProvider.Categories.COLUMN_NAME_TITLE);
            String currentTitle = (titleColumnIndex != -1) ? cursor.getString(titleColumnIndex) : "";
            cursor.close();

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setText(currentTitle);
            input.setSelection(currentTitle.length());
            input.setHint("请输入分类名称");

            builder.setTitle(R.string.edit_category_title)
                    .setMessage(R.string.edit_category_message)
                    .setView(input)
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String newTitle = input.getText().toString().trim();
                            if (!newTitle.isEmpty()) {
                                // 更新分类
                                ContentValues values = new ContentValues();
                                values.put(NotePadProvider.Categories.COLUMN_NAME_TITLE, newTitle);
                                mContentResolver.update(
                                        ContentUris.withAppendedId(
                                                Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "categories"),
                                                categoryId
                                        ),
                                        values,
                                        null,
                                        null
                                );
                                // 重新加载数据
                                reloadData();
                            } else {
                                Toast.makeText(CategoryListActivity.this, R.string.category_title_required, Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    /**
 * 删除分类
 * @param categoryId 分类ID
 */
private void deleteCategory(final long categoryId) {
    // 查询该分类下是否有笔记
    Cursor notesCursor = mContentResolver.query(
            NotePad.Notes.CONTENT_URI,
            null,
            "category_id = ?",
            new String[]{String.valueOf(categoryId)},
            null
    );
    
    if (notesCursor != null && notesCursor.getCount() > 0) {
        // 将该分类下的笔记移动到默认分类
        ContentValues values = new ContentValues();
        values.put("category_id", NotePadProvider.Categories.DEFAULT_CATEGORY_ID);
        mContentResolver.update(
                NotePad.Notes.CONTENT_URI,
                values,
                "category_id = ?",
                new String[]{String.valueOf(categoryId)}
        );
        notesCursor.close();
    }
    
    // 弹出确认对话框
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.delete_confirm_title);
    builder.setMessage(R.string.delete_category_confirm_message);
    builder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            try {
                // 删除分类
                Uri categoryUri = Uri.parse("content://" + NotePad.AUTHORITY + "/categories/" + categoryId);
                mContentResolver.delete(categoryUri, null, null);
                // 重新加载数据
                reloadData();
            } catch (Exception e) {
                Toast.makeText(CategoryListActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    });
    builder.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // 取消删除
            dialog.dismiss();
        }
    });
    builder.create().show();
}
}
