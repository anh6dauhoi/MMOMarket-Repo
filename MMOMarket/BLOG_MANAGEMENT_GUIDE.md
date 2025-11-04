# Blog Management - Migration & Usage Guide

## Overview
This guide covers the Blog Management feature for admin users, including database migration and usage instructions.

## Problem Fixed
The Blog Management flow in AdminController had errors because the `blogService` was not injected. This has been fixed by adding the proper `@Autowired` injection.

## Changes Made

### 1. AdminController.java
- **Added**: `@Autowired private com.mmo.service.BlogService blogService;`
- **Removed**: Unused imports (Resource, Sort, HttpHeaders, ZoneId, Optional)
- **Fixed**: All blog-related endpoints now work correctly

### 2. Database Migration
- **Created**: `add_status_to_blogs.sql` - Adds `status` column to Blogs table
- **Created**: `run_blog_status_migration.bat` - Batch script to run the migration

## Database Schema Update

The Blogs table now includes a `status` column:

```sql
ALTER TABLE Blogs ADD COLUMN IF NOT EXISTS status TINYINT(1) DEFAULT 1 COMMENT 'Blog status: 1=Active, 0=Inactive' AFTER image;
```

### How to Run Migration

#### Option 1: Using the batch script
```cmd
cd MMOMarket
run_blog_status_migration.bat [host] [database] [user] [password]
```

Example:
```cmd
run_blog_status_migration.bat localhost MMOMarket root mypassword
```

If you don't provide parameters, it will use defaults and prompt for password:
```cmd
run_blog_status_migration.bat
```

#### Option 2: Manual MySQL command
```cmd
mysql -u root -p MMOMarket < add_status_to_blogs.sql
```

## API Endpoints

### 1. Get All Blogs (Admin)
```
GET /admin/blogs?page=0&size=10&sort=date_desc&search=keyword
```

**Parameters:**
- `page`: Page number (default: 0)
- `size`: Page size (default: 10)
- `sort`: Sort order
  - `date_desc`: Newest first (default)
  - `date_asc`: Oldest first
  - `likes_desc`: Most liked
  - `likes_asc`: Least liked
  - `views_desc`: Most viewed
  - `views_asc`: Least viewed
  - `comments_desc`: Most commented
  - `comments_asc`: Least commented
- `search`: Search keyword (title, content, author)

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "title": "Blog Title",
      "content": "Blog content...",
      "image": "/uploads/blogs/image.png",
      "status": true,
      "author": {...},
      "views": 100,
      "likes": 50,
      "commentsCount": 10,
      "createdAt": "2025-11-04T10:00:00",
      "updatedAt": "2025-11-04T12:00:00"
    }
  ],
  "totalPages": 5,
  "totalElements": 50,
  "number": 0,
  "size": 10
}
```

### 2. Create Blog (Admin)
```
POST /admin/blogs
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "New Blog Title",
  "content": "Blog content here...",
  "image": "/uploads/blogs/image.png"
}
```

**Response:**
```json
{
  "id": 2,
  "title": "New Blog Title",
  "content": "Blog content here...",
  "image": "/uploads/blogs/image.png",
  "status": true,
  "author": {...},
  "createdAt": "2025-11-04T14:00:00"
}
```

### 3. Get Blog by ID (Admin)
```
GET /admin/blogs/{id}
```

**Response:**
```json
{
  "id": 1,
  "title": "Blog Title",
  "content": "Blog content...",
  "image": "/uploads/blogs/image.png",
  "status": true,
  "author": {...},
  "views": 100,
  "likes": 50,
  "commentsCount": 10,
  "createdAt": "2025-11-04T10:00:00",
  "updatedAt": "2025-11-04T12:00:00"
}
```

### 4. Update Blog (Admin)
```
PUT /admin/blogs/{id}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Updated Blog Title",
  "content": "Updated content...",
  "image": "/uploads/blogs/new-image.png"
}
```

**Response:**
```json
{
  "id": 1,
  "title": "Updated Blog Title",
  "content": "Updated content...",
  "image": "/uploads/blogs/new-image.png",
  "status": true,
  "updatedAt": "2025-11-04T15:00:00"
}
```

### 5. Toggle Blog Status (Admin)
```
PUT /admin/blogs/{id}/toggle-status
```

**Response:**
```json
{
  "message": "Blog activated successfully",
  "status": true
}
```

or

```json
{
  "message": "Blog deactivated successfully",
  "status": false
}
```

## Frontend Usage

### HTML Page
The admin blogs page is located at: `src/main/resources/templates/admin/blogs.html`

### Access
Navigate to: `http://localhost:8080/admin/blogs`

### Features
1. **List all blogs** with pagination and sorting
2. **Search blogs** by title, content, or author
3. **Create new blog** with title, content, and image
4. **Edit existing blog**
5. **Toggle blog status** (activate/deactivate)
6. **View blog statistics** (views, likes, comments)

## Testing

### 1. Test Blog Creation
```javascript
// Using fetch API
fetch('/admin/blogs', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: 'Test Blog',
    content: 'This is a test blog content.',
    image: '/uploads/blogs/test.png'
  })
})
.then(response => response.json())
.then(data => console.log(data));
```

### 2. Test Blog Update
```javascript
fetch('/admin/blogs/1', {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: 'Updated Test Blog',
    content: 'Updated content.',
    image: '/uploads/blogs/updated.png'
  })
})
.then(response => response.json())
.then(data => console.log(data));
```

### 3. Test Toggle Status
```javascript
fetch('/admin/blogs/1/toggle-status', {
  method: 'PUT'
})
.then(response => response.json())
.then(data => console.log(data));
```

## Error Handling

All endpoints return appropriate HTTP status codes:

- **200 OK**: Success
- **400 Bad Request**: Invalid input data
- **401 Unauthorized**: User not authenticated
- **403 Forbidden**: User is not an admin
- **404 Not Found**: Blog not found
- **500 Internal Server Error**: Server error

## Security

- All blog management endpoints require **ADMIN role**
- Authentication is checked on every request
- XSS protection on content input
- SQL injection prevention through JPA

## Troubleshooting

### Issue: "Cannot resolve symbol 'blogService'"
**Solution**: The BlogService is now properly injected with `@Autowired`. Rebuild the project.

### Issue: "Column 'status' not found"
**Solution**: Run the migration script to add the status column to the Blogs table.

### Issue: "Unauthorized" error
**Solution**: Make sure you're logged in as an admin user.

### Issue: "Blog not found"
**Solution**: Verify the blog ID exists in the database.

## Notes

- Blogs with `status = false` are inactive and won't be displayed to regular users
- Only admins can create, edit, and manage blog status
- Soft delete is supported through `isDelete` column
- Images should be uploaded to `/uploads/blogs/` directory
- Blog views and likes are tracked in separate tables (BlogViews, BlogLikes)

## Support

For issues or questions, please check:
1. Application logs in the console
2. Database connection settings in `application.properties`
3. Ensure all migrations are run successfully

