package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.data.model.PipelineState
import com.example.data.model.VerificationMode
import com.example.data.model.VerificationSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedReportScreen(
    unifiedViewModel: com.example.ui.viewmodel.UnifiedImageVerificationViewModel,
    onBack: () -> Unit
) {
    val session by unifiedViewModel.session.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unified Verification Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val currentSession = session
        
        if (currentSession != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Overall Verification Summary
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Overall Verification Summary", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Original Image / Cropped Image preview can go here.")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (VerificationMode.FACT_CHECK in currentSession.selectedModes) {
                    FactCheckSection(currentSession.factCheckState)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (VerificationMode.AI_DETECTION in currentSession.selectedModes) {
                    AiDetectionSection(currentSession.aiDetectionState)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (VerificationMode.IMAGE_SEARCH in currentSession.selectedModes) {
                    ImageSearchSection(currentSession.imageSearchState)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun FactCheckSection(state: PipelineState<com.example.data.model.FactCheckReport>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("FACT CHECK", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is PipelineState.Idle -> Text("Pending...")
                is PipelineState.Loading -> CircularProgressIndicator()
                is PipelineState.Error -> Text("Failed: ${state.message}", color = MaterialTheme.colorScheme.error)
                is PipelineState.Success -> {
                    Text("Verdict: ${state.data.verdict}")
                    Text("Claim: ${state.data.claimText}")
                }
            }
        }
    }
}

@Composable
fun AiDetectionSection(state: PipelineState<com.example.data.model.EnsembleResult>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AI IMAGE DETECTION", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is PipelineState.Idle -> Text("Pending...")
                is PipelineState.Loading -> CircularProgressIndicator()
                is PipelineState.Error -> Text("Failed: ${state.message}", color = MaterialTheme.colorScheme.error)
                is PipelineState.Success -> {
                    Text("Verdict: ${state.data.verdict.name}")
                    Text("AI Score: ${state.data.aiScore}%")
                }
            }
        }
    }
}

@Composable
fun ImageSearchSection(state: PipelineState<com.example.data.model.ImageSearchResult>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("IMAGE SEARCH", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is PipelineState.Idle -> Text("Pending...")
                is PipelineState.Loading -> CircularProgressIndicator()
                is PipelineState.Error -> Text("Failed: ${state.message}", color = MaterialTheme.colorScheme.error)
                is PipelineState.Success -> {
                    Text("Matches found: ${state.data.matches.size}")
                }
            }
        }
    }
}
