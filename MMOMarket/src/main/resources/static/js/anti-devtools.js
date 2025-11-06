/**
 * Anti-DevTools Protection
 * Ngăn chặn người dùng mở Developer Tools (F12)
 *
 * Lưu ý: Đây chỉ là biện pháp răn đe, không thể chặn hoàn toàn.
 * Người dùng có kinh nghiệm vẫn có thể vô hiệu hóa script này.
 */
(function() {
    'use strict';

    // ============================================
    // CẤU HÌNH - Thay đổi giá trị này để bật/tắt
    // ============================================
    const CONFIG = {
        enabled: true,                    // true = bật, false = tắt
        blockContextMenu: false,           // Chặn chuột phải
        blockKeyboardShortcuts: true,     // Chặn phím tắt (F12, Ctrl+Shift+I, etc)
        detectDevTools: true,             // Phát hiện DevTools đang mở
        showWarning: true,                // Hiển thị cảnh báo
        warningMessage: 'Developer tools are disabled on this site for security reasons.',
        redirectOnPersist: false,         // Chuyển hướng nếu cố tình mở DevTools
        redirectUrl: '/security-warning', // URL chuyển hướng
        forceReloadOnDetect: true,        // Bắt buộc reload khi phát hiện DevTools đang mở
        reloadMessage: 'Developer Tools detected! Please close DevTools and reload the page to continue.',
        injectWarningElements: true,      // Chèn các element cảnh báo vào trang
        maxWarningElements: 20            // Số lượng element cảnh báo tối đa
    };

    // Nếu tắt chức năng, dừng ngay
    if (!CONFIG.enabled) {
        console.log('[Anti-DevTools] Disabled');
        return;
    }

    // ============================================
    // BIẾN TOÀN CỤC
    // ============================================
    let overlay = null;
    let devToolsOpen = false;
    let warningCount = 0;
    const MAX_WARNINGS = 3;
    let isDevToolsCurrentlyOpen = false; // Trạng thái DevTools đang mở

    // ============================================
    // HIỂN THỊ CẢNH BÁO
    // ============================================
    function showWarning(forceReload = false) {
        if (!CONFIG.showWarning) return;
        if (overlay) return; // Đã hiển thị rồi

        warningCount++;

        // Tạo overlay
        overlay = document.createElement('div');
        overlay.id = 'anti-devtools-overlay';
        overlay.style.cssText = `
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, ${forceReload ? '0.95' : '0.85'});
            z-index: 999999;
            display: flex;
            align-items: center;
            justify-content: center;
            animation: fadeIn 0.3s ease-out;
        `;

        // Nội dung cảnh báo - khác nhau nếu bắt buộc reload
        const content = forceReload ? `
            <div style="
                background: white;
                padding: 30px;
                border-radius: 12px;
                max-width: 500px;
                box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
                animation: slideIn 0.3s ease-out;
            ">
                <div style="text-align: center; margin-bottom: 20px;">
                    <div style="
                        width: 100px;
                        height: 100px;
                        background: linear-gradient(135deg, #dc2626, #b91c1c);
                        border-radius: 50%;
                        margin: 0 auto 15px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        animation: pulse 2s infinite;
                    ">
                        <svg style="width: 60px; height: 60px; color: white;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                        </svg>
                    </div>
                    <h2 style="
                        font-size: 26px;
                        font-weight: 700;
                        color: #dc2626;
                        margin: 0 0 10px 0;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">Access Blocked</h2>
                </div>
                
                <div style="
                    background: #fee2e2;
                    border: 2px solid #dc2626;
                    padding: 20px;
                    margin-bottom: 20px;
                    border-radius: 8px;
                    text-align: center;
                ">
                    <p style="
                        margin: 0 0 15px 0;
                        color: #991b1b;
                        font-size: 16px;
                        font-weight: 600;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">${CONFIG.reloadMessage}</p>
                    
                    <p style="
                        margin: 0;
                        color: #b91c1c;
                        font-size: 14px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">
                        <strong>⚠️ This page cannot be used while Developer Tools are open.</strong>
                    </p>
                </div>
                
                <div style="
                    background: #fef2f2;
                    border-left: 4px solid #dc2626;
                    padding: 12px 15px;
                    margin-bottom: 20px;
                    border-radius: 4px;
                ">
                    <p style="
                        margin: 0;
                        color: #7f1d1d;
                        font-size: 13px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">
                        <strong>Instructions:</strong><br>
                        1. Close Developer Tools (F12)<br>
                        2. Click the Reload button below<br>
                        3. Do not open Developer Tools again
                    </p>
                </div>
                
                <div style="text-align: center;">
                    <button id="anti-devtools-reload" style="
                        padding: 14px 40px;
                        background: linear-gradient(135deg, #dc2626, #b91c1c);
                        color: white;
                        border: none;
                        border-radius: 8px;
                        font-weight: 700;
                        font-size: 16px;
                        cursor: pointer;
                        transition: all 0.2s;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                        box-shadow: 0 4px 12px rgba(220, 38, 38, 0.4);
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    " 
                    onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 6px 20px rgba(220, 38, 38, 0.5)';"
                    onmouseout="this.style.transform='translateY(0)'; this.style.boxShadow='0 4px 12px rgba(220, 38, 38, 0.4)';">
                        🔄 Reload Page Now
                    </button>
                    <p style="
                        margin: 15px 0 0 0;
                        color: #6b7280;
                        font-size: 12px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">Page will auto-reload in <span id="countdown">10</span> seconds...</p>
                </div>
            </div>
        ` : `
            <div style="
                background: white;
                padding: 30px;
                border-radius: 12px;
                max-width: 450px;
                box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
                animation: slideIn 0.3s ease-out;
            ">
                <div style="text-align: center; margin-bottom: 20px;">
                    <div style="
                        width: 80px;
                        height: 80px;
                        background: linear-gradient(135deg, #dc2626, #ef4444);
                        border-radius: 50%;
                        margin: 0 auto 15px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    ">
                        <svg style="width: 50px; height: 50px; color: white;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                        </svg>
                    </div>
                    <h2 style="
                        font-size: 24px;
                        font-weight: 700;
                        color: #dc2626;
                        margin: 0 0 10px 0;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">Security Warning</h2>
                </div>
                
                <p style="
                    color: #374151;
                    line-height: 1.6;
                    margin: 0 0 20px 0;
                    text-align: center;
                    font-size: 15px;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                ">${CONFIG.warningMessage}</p>
                
                <div style="
                    background: #fef2f2;
                    border-left: 4px solid #dc2626;
                    padding: 12px 15px;
                    margin-bottom: 20px;
                    border-radius: 4px;
                ">
                    <p style="
                        margin: 0;
                        color: #991b1b;
                        font-size: 13px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    ">
                        <strong>Warning ${warningCount}/${MAX_WARNINGS}:</strong> 
                        ${warningCount >= MAX_WARNINGS ? 'Final warning! Continued attempts may result in access restriction.' : 'Please close developer tools to continue.'}
                    </p>
                </div>
                
                <div style="text-align: center;">
                    <button id="anti-devtools-close" style="
                        padding: 12px 30px;
                        background: linear-gradient(135deg, #dc2626, #ef4444);
                        color: white;
                        border: none;
                        border-radius: 8px;
                        font-weight: 600;
                        font-size: 14px;
                        cursor: pointer;
                        transition: all 0.2s;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                        box-shadow: 0 4px 12px rgba(220, 38, 38, 0.3);
                    " 
                    onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 6px 16px rgba(220, 38, 38, 0.4)';"
                    onmouseout="this.style.transform='translateY(0)'; this.style.boxShadow='0 4px 12px rgba(220, 38, 38, 0.3)';">
                        I Understand
                    </button>
                </div>
            </div>
        `;

        overlay.innerHTML = content;

        // Thêm CSS animations
        const style = document.createElement('style');
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes slideIn {
                from { transform: translateY(-30px); opacity: 0; }
                to { transform: translateY(0); opacity: 1; }
            }
            @keyframes pulse {
                0%, 100% { transform: scale(1); }
                50% { transform: scale(1.05); }
            }
            @keyframes fadeInOut {
                0% { opacity: 0; transform: translate(-50%, -50%) scale(0.5); }
                10% { opacity: 1; transform: translate(-50%, -50%) scale(1.1); }
                20% { transform: translate(-50%, -50%) scale(1); }
                80% { opacity: 1; transform: translate(-50%, -50%) scale(1); }
                100% { opacity: 0; transform: translate(-50%, -50%) scale(0.8); }
            }
            @keyframes shake {
                0%, 100% { transform: translateX(0); }
                10%, 30%, 50%, 70%, 90% { transform: translateX(-5px); }
                20%, 40%, 60%, 80% { transform: translateX(5px); }
            }
        `;
        document.head.appendChild(style);

        document.body.appendChild(overlay);

        // Xử lý nút - khác nhau cho reload vs close
        if (forceReload) {
            const reloadBtn = document.getElementById('anti-devtools-reload');
            if (reloadBtn) {
                reloadBtn.addEventListener('click', function() {
                    location.reload();
                });
            }

            // Đếm ngược tự động reload
            let countdown = 10;
            const countdownEl = document.getElementById('countdown');
            const countdownInterval = setInterval(function() {
                countdown--;
                if (countdownEl) {
                    countdownEl.textContent = countdown;
                }
                if (countdown <= 0) {
                    clearInterval(countdownInterval);
                    location.reload();
                }
            }, 1000);

            // Chặn tương tác với trang phía sau overlay (KHÔNG chặn overlay)
            document.addEventListener('click', function blockClicks(e) {
                // Cho phép click trên overlay và các phần tử con của nó
                if (overlay && !overlay.contains(e.target)) {
                    e.preventDefault();
                    e.stopPropagation();
                }
            }, true);

            document.addEventListener('keydown', function blockKeys(e) {
                // Không chặn Tab, Enter, Space để có thể tương tác với nút Reload
                if (e.key === 'Tab' || e.key === 'Enter' || e.key === ' ') {
                    return; // Cho phép
                }
                e.preventDefault();
                e.stopPropagation();
            }, true);

        } else {
            const closeBtn = document.getElementById('anti-devtools-close');
            if (closeBtn) {
                closeBtn.addEventListener('click', closeWarning);
            }
        }

        // Chuyển hướng nếu quá số lần cảnh báo
        if (CONFIG.redirectOnPersist && warningCount >= MAX_WARNINGS && !forceReload) {
            setTimeout(() => {
                window.location.href = CONFIG.redirectUrl;
            }, 3000);
        }
    }

    function closeWarning() {
        if (overlay) {
            overlay.style.animation = 'fadeOut 0.3s ease-out';
            setTimeout(() => {
                overlay?.remove();
                overlay = null;
            }, 300);
        }
    }

    // ============================================
    // CHẶN CHUỘT PHẢI (CONTEXT MENU)
    // ============================================
    if (CONFIG.blockContextMenu) {
        document.addEventListener('contextmenu', function(e) {
            e.preventDefault();
            e.stopPropagation();
            showWarning(false);
            return false;
        }, { capture: true });
    }

    // ============================================
    // CHẶN PHÍM TẮT
    // ============================================
    if (CONFIG.blockKeyboardShortcuts) {
        document.addEventListener('keydown', function(e) {
            const key = (e.key || '').toLowerCase();
            const blocked =
                e.key === 'F12' ||                                              // F12
                (e.ctrlKey && e.shiftKey && key === 'i') ||                    // Ctrl+Shift+I
                (e.ctrlKey && e.shiftKey && key === 'j') ||                    // Ctrl+Shift+J
                (e.ctrlKey && e.shiftKey && key === 'c') ||                    // Ctrl+Shift+C
                (e.ctrlKey && key === 'u') ||                                   // Ctrl+U (view source)
                (e.metaKey && e.altKey && key === 'i') ||                      // Cmd+Option+I (Mac)
                (e.metaKey && e.altKey && key === 'j') ||                      // Cmd+Option+J (Mac)
                (e.metaKey && e.altKey && key === 'c');                        // Cmd+Option+C (Mac)

            if (blocked) {
                e.preventDefault();
                e.stopPropagation();
                showWarning(false);
                return false;
            }
        }, { capture: true });
    }

    // ============================================
    // PHÁT HIỆN DEVTOOLS ĐANG MỞ
    // ============================================
    if (CONFIG.detectDevTools) {
        // Method 1: Kiểm tra kích thước cửa sổ
        let lastDevToolsState = false;
        let devToolsDetectedCount = 0;

        function checkDevTools() {
            const widthThreshold = 160;
            const heightThreshold = 160;
            const isOpen =
                (window.outerWidth - window.innerWidth > widthThreshold) ||
                (window.outerHeight - window.innerHeight > heightThreshold);

            if (isOpen && !lastDevToolsState) {
                devToolsDetectedCount++;
                devToolsOpen = true;
                isDevToolsCurrentlyOpen = true;

                // Nếu bật forceReloadOnDetect, hiển thị popup reload ngay
                if (CONFIG.forceReloadOnDetect) {
                    showWarning(true); // true = force reload
                } else {
                    showWarning(false);
                }
            }

            // Cập nhật trạng thái
            if (!isOpen && lastDevToolsState) {
                isDevToolsCurrentlyOpen = false;
            }

            lastDevToolsState = isOpen;
        }

        // Kiểm tra định kỳ
        setInterval(checkDevTools, 500); // Giảm từ 1000ms xuống 500ms để phát hiện nhanh hơn

        // Method 2: Debugger trap (aggressive - có thể gây khó chịu)
        // Uncomment để kích hoạt
        /*
        setInterval(function() {
            const before = new Date();
            debugger;
            const after = new Date();
            const diff = after.getTime() - before.getTime();

            if (diff > 100) {
                if (CONFIG.forceReloadOnDetect) {
                    showWarning(true);
                } else {
                    showWarning(false);
                }
            }
        }, 1000);
        */

        // Method 3: Console detection
        const element = new Image();
        Object.defineProperty(element, 'id', {
            get: function() {
                devToolsOpen = true;
                isDevToolsCurrentlyOpen = true;
                if (CONFIG.forceReloadOnDetect) {
                    showWarning(true);
                } else {
                    showWarning(false);
                }
            }
        });

        setInterval(function() {
            console.dir(element);
        }, 2000);
    }

    // ============================================
    // DISABLE TEXT SELECTION (Optional)
    // ============================================
    // Uncomment để chặn chọn text
    /*
    document.addEventListener('selectstart', function(e) {
        e.preventDefault();
        return false;
    });

    document.addEventListener('copy', function(e) {
        e.preventDefault();
        return false;
    });
    */

    // ============================================
    // LOG
    // ============================================
    console.log('%c[Anti-DevTools] Protection Active', 'color: #dc2626; font-weight: bold; font-size: 14px;');
    console.log('%cWarning: Unauthorized access to developer tools is monitored.', 'color: #dc2626; font-size: 12px;');

    // ============================================
    // CHÈN CÁC ELEMENT CẢNH BÁO NGẪU NHIÊN
    // ============================================
    function injectRandomWarnings() {
        if (!CONFIG.injectWarningElements || warningCount >= CONFIG.maxWarningElements) return;

        const messages = [
            'Warning: Your actions are being tracked.',
            'Caution: Opening Developer Tools may result in being banned.',
            'Security Warning: Please close Developer Tools.',
            'Caution: This site does not allow Developer Tools.',
            'Warning: You are violating site policy.',
            'Caution: The system has detected that you are opening Developer Tools.',
            'Security Warning: Your actions may be recorded.',
            'Caution: To continue, please close Developer Tools.',
            'Warning: Violation of security policy!',
            'Caution: Opening Developer Tools may pose a security risk.'
        ];

        const randomMessage = messages[Math.floor(Math.random() * messages.length)];

        // Tạo element cảnh báo
        const warningElement = document.createElement('div');
        warningElement.className = 'anti-devtools-warning';
        warningElement.style.cssText = `
            position: fixed;
            top: ${Math.random() * 90}%;
            left: ${Math.random() * 90}%;
            transform: translate(-50%, -50%);
            background: rgba(220, 38, 38, 0.9);
            color: white;
            padding: 15px 25px;
            border-radius: 8px;
            font-weight: 600;
            font-size: 14px;
            z-index: 999999;
            pointer-events: none;
            animation: fadeInOut 4s ease-in-out;
        `;
        warningElement.innerText = randomMessage;

        document.body.appendChild(warningElement);

        // Tăng số lượng cảnh báo đã chèn
        warningCount++;

        // Tự động xóa sau 4 giây
        setTimeout(() => {
            warningElement.remove();
        }, 4000);
    }

    // Tạo các cảnh báo ngẫu nhiên khi phát hiện DevTools
    setInterval(() => {
        if (devToolsOpen) {
            injectRandomWarnings();
        }
    }, 3000);

})();
