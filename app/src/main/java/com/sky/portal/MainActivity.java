// 原生回调：接收到应用列表
        window.showNativeApps = function(apps) {
            if (!apps || apps.length === 0) return alert("未获取到应用");
            
            modalTitle.innerText = "选择应用";
            appListContainer.innerHTML = "";
            const fragment = document.createDocumentFragment();
            
            apps.forEach((app, index) => {
                let item = document.createElement('div');
                item.className = 'app-item';
                let iconId = "icon_" + index;
                
                item.innerHTML = `
                    <img class="app-icon" id="${iconId}" src="" alt="">
                    <div class="app-info">
                        <div class="app-name">${app.appName}</div>
                        <div class="app-pkg">${app.packageName}</div>
                    </div>
                `;
                
                item.addEventListener('click', () => {
                    playSound('tap'); closeNativeAppModal();
                    
                    modalTitle.innerText = "读取地图中...";
                    appListContainer.innerHTML = `<div style="text-align:center; padding: 20px; color:#00ffaa;">正在扫描 APK (含分包) 地图...</div>`;
                    nativeAppModal.classList.add('show');
                    
                    // 👇 核心修改：传包名给原生，让原生自己去遍历主包和分包
                    window.AndroidNative.getApkLevels(app.packageName);
                });
                
                fragment.appendChild(item);
                
                setTimeout(() => {
                    window.AndroidNative.getAppIcon(app.packageName, iconId);
                }, index * 20);
            });

            appListContainer.appendChild(fragment);
        };
