package com.comiclens.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val cs = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = cs) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val vm: MainVm = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainVm(ctx.applicationContext as Application) as T
            }
        }
    )
    val nav = rememberNavController()
    NavHost(nav, "home") {
        composable("home") { Home(vm) { nav.navigate("new") } }
        composable("new") { NewJob(vm, onStarted = { nav.navigate("progress") }, onBack = { nav.popBackStack() }) }
        composable("progress") { ProgressScreen(vm) { nav.popBackStack("home", false) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(vm: MainVm, onNew: () -> Unit) {
    val jobs by vm.jobs.collectAsState()
    val ctx = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("ComicLens") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New translation") })
        }
    ) { pad ->
        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Abhi koi job nahi. Naya translation shuru karo.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jobs) { j ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(j.name, style = MaterialTheme.typography.titleMedium)
                            Text("${Langs.name(j.src)} → ${Langs.name(j.tgt)} · ${j.status} · ${j.done}/${j.total}")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (j.status == "Done") {
                                    TextButton(onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(j.outUri), "application/zip")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        ctx.startActivity(Intent.createChooser(intent, "Open"))
                                    }) { Text("Open") }
                                }
                                TextButton(onClick = { vm.delete(j) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LangPicker(label: String, code: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(expanded, { expanded = it }) {
            OutlinedTextField(
                value = Langs.name(code),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(expanded, { expanded = false }) {
                Langs.all.forEach { (c, n) ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { onChange(c); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewJob(vm: MainVm, onStarted: () -> Unit, onBack: () -> Unit) {
    var input by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var src by remember { mutableStateOf("ja") }
    var tgt by remember { mutableStateOf("en") }
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            input = uri
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) name = c.getString(i).substringBeforeLast(".")
            }
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { out ->
        val i = input
        if (out != null && i != null) { vm.start(name, i, out, src, tgt); onStarted() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("New translation") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (input == null) "ZIP / CBZ chuno" else name)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LangPicker("From", src, { src = it }, Modifier.weight(1f))
                IconButton(onClick = { val t = src; src = tgt; tgt = t }) { Icon(Icons.Filled.SwapHoriz, "Swap") }
                LangPicker("To", tgt, { tgt = it }, Modifier.weight(1f))
            }
            Text("Pehli baar language model download hoga (internet chahiye). Uske baad sab offline.",
                style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { create.launch("${name}_$tgt.cbz") },
                enabled = input != null && src != tgt, modifier = Modifier.fillMaxWidth()
            ) { Text("Start") }
        }
    }
}

@Composable
fun ProgressScreen(vm: MainVm, onDone: () -> Unit) {
    val (d, t) = vm.progress.collectAsState().value
    val err by vm.error.collectAsState()
    val running by vm.running.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (err != null) {
            Text(err!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        } else {
            if (t == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { d / t.toFloat() }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(if (t == 0) "Taiyari ho rahi hai..." else if (running) "$d / $t pages" else "Ho gaya! $t pages")
        }
        Spacer(Modifier.height(24.dp))
        if (running) OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
        else Button(onClick = onDone) { Text("Home") }
    }
}
