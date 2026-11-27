const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const APK_PATH = path.join(__dirname, 'apk', 'golive.apk');
const HINA_PATH = path.join(__dirname, 'apk', 'golive.hina');

function getFileSizeMB(filePath) {
    try {
        const stats = fs.statSync(filePath);
        return (stats.size / (1024 * 1024)).toFixed(1) + ' MB';
    } catch (e) {
        return '34.5 MB';
    }
}

const server = http.createServer((req, res) => {
    const url = req.url.split('?')[0];

    // Download APK routes
    if (url === '/apk/golive.apk' || url === '/golive.apk' || url === '/downloads/golive.apk') {
        const targetPath = fs.existsSync(APK_PATH) ? APK_PATH : HINA_PATH;
        if (!fs.existsSync(targetPath)) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            return res.end('APK file not found. Please compile the app first.');
        }
        const stat = fs.statSync(targetPath);
        res.writeHead(200, {
            'Content-Type': 'application/vnd.android.package-archive',
            'Content-Disposition': 'attachment; filename="golive.apk"',
            'Content-Length': stat.size,
            'Cache-Control': 'no-cache'
        });
        const readStream = fs.createReadStream(targetPath);
        return readStream.pipe(res);
    }

    // Download HINA routes
    if (url === '/apk/golive.hina' || url === '/golive.hina' || url === '/downloads/golive.hina') {
        const targetPath = fs.existsSync(HINA_PATH) ? HINA_PATH : APK_PATH;
        if (!fs.existsSync(targetPath)) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            return res.end('File not found. Please compile the app first.');
        }
        const stat = fs.statSync(targetPath);
        res.writeHead(200, {
            'Content-Type': 'application/octet-stream',
            'Content-Disposition': 'attachment; filename="golive.hina"',
            'Content-Length': stat.size,
            'Cache-Control': 'no-cache'
        });
        const readStream = fs.createReadStream(targetPath);
        return readStream.pipe(res);
    }

    // Main Web Portal
    const fileSize = getFileSizeMB(APK_PATH);
    const host = req.headers['x-forwarded-host'] || req.headers.host || 'localhost:3000';
    const proto = req.headers['x-forwarded-proto'] || 'https';
    const downloadUrl = `${proto}://${host}/apk/golive.apk`;
    const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(downloadUrl)}`;

    const html = `<!DOCTYPE html>
<html lang="hi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FocusStudy AI • Download golive.apk</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
            background-color: #070B14;
            color: #F1F5F9;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 32px 16px;
        }
        .container {
            max-width: 680px;
            width: 100%;
        }
        .card {
            background: #0F172A;
            border: 1px solid #1E293B;
            border-radius: 24px;
            padding: 32px 24px;
            box-shadow: 0 20px 40px -15px rgba(0,0,0,0.7);
            margin-bottom: 24px;
        }
        .header {
            text-align: center;
            margin-bottom: 28px;
        }
        .app-icon {
            width: 80px;
            height: 80px;
            background: linear-gradient(135deg, #6366F1 0%, #06B6D4 100%);
            border-radius: 22px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 38px;
            box-shadow: 0 10px 25px -5px rgba(99, 102, 241, 0.5);
            margin-bottom: 16px;
        }
        h1 {
            font-size: 28px;
            font-weight: 800;
            color: #FFFFFF;
            letter-spacing: -0.5px;
            margin-bottom: 6px;
        }
        .tagline {
            color: #14B8A6;
            font-size: 15px;
            font-weight: 600;
        }
        .badges {
            display: flex;
            justify-content: center;
            gap: 8px;
            flex-wrap: wrap;
            margin: 16px 0 24px 0;
        }
        .badge {
            background: rgba(30, 41, 59, 0.8);
            border: 1px solid #334155;
            padding: 6px 12px;
            border-radius: 999px;
            font-size: 12px;
            font-weight: 600;
            color: #94A3B8;
        }
        .badge-live {
            background: rgba(16, 185, 129, 0.15);
            border-color: #10B981;
            color: #34D399;
        }
        .btn-download {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            width: 100%;
            background: linear-gradient(135deg, #10B981 0%, #059669 100%);
            color: #FFFFFF;
            text-decoration: none;
            padding: 18px 24px;
            border-radius: 16px;
            font-size: 18px;
            font-weight: 700;
            box-shadow: 0 10px 25px -5px rgba(16, 185, 129, 0.4);
            transition: transform 0.2s, box-shadow 0.2s;
            margin-bottom: 12px;
        }
        .btn-download:hover {
            transform: translateY(-2px);
            box-shadow: 0 15px 30px -5px rgba(16, 185, 129, 0.5);
        }
        .btn-secondary {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            width: 100%;
            background: #1E293B;
            border: 1px solid #334155;
            color: #CBD5E1;
            text-decoration: none;
            padding: 14px 20px;
            border-radius: 14px;
            font-size: 14px;
            font-weight: 600;
            transition: background 0.2s;
        }
        .btn-secondary:hover {
            background: #253349;
            color: #FFFFFF;
        }
        .meta-box {
            background: #090E1A;
            border: 1px solid #1E293B;
            border-radius: 16px;
            padding: 18px;
            margin-top: 24px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 12px;
            color: #94A3B8;
            line-height: 1.8;
        }
        .meta-row {
            display: flex;
            justify-content: space-between;
            border-bottom: 1px dashed #1E293B;
            padding: 4px 0;
        }
        .meta-row:last-child { border-bottom: none; }
        .meta-label { color: #64748B; }
        .meta-val { color: #38BDF8; font-weight: 700; }
        .qr-section {
            text-align: center;
            margin-top: 24px;
            padding-top: 20px;
            border-top: 1px solid #1E293B;
        }
        .qr-img {
            background: white;
            padding: 10px;
            border-radius: 12px;
            margin-top: 10px;
            box-shadow: 0 8px 20px rgba(0,0,0,0.5);
        }
        .steps {
            margin-top: 20px;
            padding-left: 20px;
            color: #94A3B8;
            font-size: 13px;
            line-height: 1.8;
        }
        .steps strong { color: #E2E8F0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="header">
                <div class="app-icon">🎓</div>
                <h1>FocusStudy AI</h1>
                <div class="tagline">Smart Timetable • Focus Lock App Blocker • Gemini AI Tutor</div>
                <div class="badges">
                    <span class="badge badge-live">● Ready to Download</span>
                    <span class="badge">Amazon Appstore Ready</span>
                    <span class="badge">AdMob Enabled</span>
                    <span class="badge">${fileSize}</span>
                </div>
            </div>

            <!-- Primary Direct Download APK Button -->
            <a href="/apk/golive.apk" download="golive.apk" class="btn-download">
                <span>⬇️</span>
                <span>Download golive.apk (${fileSize})</span>
            </a>

            <!-- Secondary Download HINA Button -->
            <a href="/apk/golive.hina" download="golive.hina" class="btn-secondary">
                <span>📦</span>
                <span>Alternative: Download golive.hina</span>
            </a>

            <!-- Meta details -->
            <div class="meta-box">
                <div class="meta-row">
                    <span class="meta-label">File Name:</span>
                    <span class="meta-val">golive.apk / golive.hina</span>
                </div>
                <div class="meta-row">
                    <span class="meta-label">Package Name:</span>
                    <span class="meta-val">com.aistudio.focusstudy.aizkpr</span>
                </div>
                <div class="meta-row">
                    <span class="meta-label">File Size:</span>
                    <span class="meta-val">${fileSize}</span>
                </div>
                <div class="meta-row">
                    <span class="meta-label">Platform Target:</span>
                    <span class="meta-val">Android 7.0+ & Amazon Fire OS</span>
                </div>
                <div class="meta-row">
                    <span class="meta-label">AI Engine:</span>
                    <span class="meta-val">Gemini 3.5 Flash-Lite (High Quota)</span>
                </div>
            </div>

            <!-- Mobile QR Code Download -->
            <div class="qr-section">
                <p style="font-size: 13px; color: #94A3B8; font-weight: 600;">📱 Scan with Phone Camera to Download directly:</p>
                <img class="qr-img" src="${qrUrl}" alt="Scan to Download APK" width="160" height="160" />
            </div>

            <!-- How to install -->
            <div style="margin-top: 24px;">
                <h3 style="font-size: 15px; color: #FFFFFF; margin-bottom: 8px;">📲 Mobile Phone me Install kaise karein:</h3>
                <ol class="steps">
                    <li>Upar diye gaye <strong>"Download golive.apk"</strong> button par tap karein.</li>
                    <li>Download complete hone par notification bar se APK file open karein.</li>
                    <li>Agar prompt aaye, toh <em>"Install unknown apps"</em> ki permission ko <strong>Allow</strong> karein.</li>
                    <li><strong>Install</strong> button press karein aur FocusStudy AI enjoy karein!</li>
                </ol>
            </div>
        </div>
    </div>
</body>
</html>`;

    res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'no-cache'
    });
    res.end(html);
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Web portal listening on port ${PORT}`);
});
