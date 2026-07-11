package com.sample.scopesample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sample.scopesample.ui.theme.ScopeSampleTheme
import kotlinx.coroutines.flow.StateFlow

sealed class Screen {
    data object Home : Screen()
    data object FlowDemo : Screen()
    data object ThreadDemo : Screen()
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val flowViewModel: FlowViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScopeSampleTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                if (currentScreen != Screen.Home) {
                    BackHandler { currentScreen = Screen.Home }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    when (currentScreen) {
                                        Screen.Home -> "Scope Sample Home"
                                        Screen.FlowDemo -> "Flow 策略演示"
                                        Screen.ThreadDemo -> "线程调度分析"
                                    }
                                )
                            },
                            navigationIcon = {
                                if (currentScreen != Screen.Home) {
                                    IconButton(onClick = { currentScreen = Screen.Home }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.Home -> HomeScreen(
                                onNavigateToFlow = { currentScreen = Screen.FlowDemo },
                                onNavigateToThread = { currentScreen = Screen.ThreadDemo }
                            )
                            Screen.FlowDemo -> FlowDemoScreen(flowViewModel)
                            Screen.ThreadDemo -> ThreadDemoScreen(mainViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigateToFlow: () -> Unit, onNavigateToThread: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onNavigateToFlow, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("1. 进入 Flow 策略演示 (stateIn)", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateToThread, modifier = Modifier.fillMaxWidth().height(64.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            Text("2. 进入 线程调度分析 (Dispatcher)", fontSize = 16.sp)
        }
    }
}

@Composable
fun FlowDemoScreen(viewModel: FlowViewModel) {
    val logText by viewModel.logText.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        
        Text("基础策略演示:", fontWeight = FontWeight.Bold)
        
        FlowItemCard(
            title = "SharingStarted.Eagerly",
            subtitle = "立即启动，永不停止",
            flow = viewModel.eagerlyFlow
        )
        
        FlowItemCard(
            title = "SharingStarted.Lazily",
            subtitle = "首次订阅启动，永不停止",
            flow = viewModel.lazilyFlow
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("WhileSubscribed 深度对比:", fontWeight = FontWeight.Bold)
        Text("停止订阅5s以上后再重新开启，观察历史值现象：", fontSize = 12.sp, color = Color.Gray)
        
        FlowItemCard(
            title = "WhileSubscribed(5000)", 
            subtitle = "历史值【永不失效】。重连时先看旧值，再变0", 
            flow = viewModel.whileSubscribedKeepFlow
        )
        
        FlowItemCard(
            title = "WhileSubscribed(5000, 0)", 
            subtitle = "历史值【立即失效】。重连时直接显示 0", 
            flow = viewModel.whileSubscribedExpiredFlow
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Flow 生命周期日志:", fontSize = 11.sp, color = Color.Gray)
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).background(Color(0xFFEFEFEF), RoundedCornerShape(4.dp)).padding(8.dp)) {
            Text(text = logText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun FlowItemCard(
    title: String, 
    subtitle: String, 
    flow: StateFlow<Int>
) {
    var isActive by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = subtitle, fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
                if (isActive) {
                    val count by flow.collectAsState()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("当前计数: $count", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Button(
                onClick = { isActive = !isActive }, 
                modifier = Modifier.height(32.dp), 
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(if (isActive) "取消订阅" else "开始订阅", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ThreadDemoScreen(viewModel: MainViewModel) {
    val logText by viewModel.logText.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.runTrapDemo() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("陷阱演示 (30+线程)", fontSize = 11.sp)
            }
            Button(onClick = { viewModel.runOptimizedDemo() }, modifier = Modifier.weight(1f)) {
                Text("优化演示 (20线程)", fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "线程快照日志:", fontSize = 11.sp, color = Color.Gray)
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text(text = logText, color = Color.Green, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
        }
    }
}
