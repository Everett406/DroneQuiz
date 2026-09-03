package com.drone.quiz

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drone.quiz.data.settings.AppSettings
import com.drone.quiz.ui.nav.AppRoot
import com.drone.quiz.ui.theme.DroneTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        enableEdgeToEdge()
        setContent {
            val settings by ServiceLocator.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            DroneTheme(themeMode = settings.themeMode, fontLevel = settings.fontLevel) {
                val context = LocalContext.current
                var crashReport by remember { mutableStateOf(CrashGuard.readLast(context)) }
                var ready by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    runCatching {
                        ServiceLocator.repo.ensureBankLoaded(applicationContext)
                    }
                    ready = true
                }

                when {
                    // 上次崩溃报告优先展示
                    crashReport != null -> CrashReportScreen(
                        report = crashReport!!,
                        onClose = {
                            CrashGuard.clear(context)
                            crashReport = null
                        }
                    )
                    ready -> AppRoot(settings = settings)
                    // 加载期给出可见的启动画面
                    else -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("正在加载题库…", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportScreen(report: String, onClose: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1811))
            .padding(20.dp)
    ) {
        Text(
            "启动报告",
            color = Color(0xFFEFE9DC),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "上次异常退出，以下是详细信息（可截图反馈）",
            color = Color(0xFF9A907F),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF26221C))
        ) {
            Text(
                report,
                color = Color(0xFFE06666),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
        Row {
            OutlinedButton(
                onClick = {
                    runCatching {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("crash", report))
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("复制") }
            Spacer(Modifier.height(0.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) { Text("清除并继续") }
        }
        Spacer(Modifier.height(10.dp))
    }
}
