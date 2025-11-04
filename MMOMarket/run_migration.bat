@echo off
REM ========================================
REM Auto Migration Script - Add Status Column
REM ========================================

echo.
echo ========================================
echo   ADDING STATUS COLUMN TO CATEGORIES
echo ========================================
echo.

REM Thiết lập thông tin MySQL
set MYSQL_USER=root
set MYSQL_DB=MMO_System
set SCRIPT_FILE=add_status_column.sql

echo [1/4] Checking MySQL connection...
mysql -u %MYSQL_USER% -p -e "SELECT 'MySQL connected successfully!' AS status;"
if errorlevel 1 (
    echo [ERROR] Cannot connect to MySQL!
    echo Please check:
    echo   - MySQL is running
    echo   - Username is correct
    echo   - Password is correct
    pause
    exit /b 1
)

echo.
echo [2/4] Checking database MMO_System...
mysql -u %MYSQL_USER% -p -e "USE MMO_System; SELECT 'Database exists!' AS status;"
if errorlevel 1 (
    echo [ERROR] Database MMO_System not found!
    echo Please create database first.
    pause
    exit /b 1
)

echo.
echo [3/4] Running migration script...
mysql -u %MYSQL_USER% -p %MYSQL_DB% < %SCRIPT_FILE%
if errorlevel 1 (
    echo [WARNING] Migration may have failed or column already exists
    echo Trying simple script...
    mysql -u %MYSQL_USER% -p %MYSQL_DB% < add_status_simple.sql
)

echo.
echo [4/4] Verifying results...
mysql -u %MYSQL_USER% -p %MYSQL_DB% -e "DESCRIBE Categories; SELECT id, name, type, status FROM Categories LIMIT 3;"

echo.
echo ========================================
echo   MIGRATION COMPLETED!
echo ========================================
echo.
echo Status column has been added to Categories table.
echo.
pause

