@echo off
REM Migration script for adding status column to Blogs table
REM Usage: run_blog_status_migration.bat [host] [database] [user] [password]

SET HOST=%1
SET DATABASE=%2
SET USER=%3
SET PASSWORD=%4

IF "%HOST%"=="" SET HOST=localhost
IF "%DATABASE%"=="" SET DATABASE=MMOMarket
IF "%USER%"=="" SET USER=root
IF "%PASSWORD%"=="" SET /P PASSWORD=Enter MySQL password:

echo Running migration: Add status to Blogs table...
echo Host: %HOST%
echo Database: %DATABASE%
echo User: %USER%
echo.

mysql -h %HOST% -u %USER% -p%PASSWORD% %DATABASE% < add_status_to_blogs.sql

IF %ERRORLEVEL% EQU 0 (
    echo.
    echo Migration completed successfully!
    echo.
) ELSE (
    echo.
    echo Migration failed! Error code: %ERRORLEVEL%
    echo.
)

pause

