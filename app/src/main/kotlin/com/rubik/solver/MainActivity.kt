package com.rubik.solver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rubik.solver.feature.editor.EditorScreen
import com.rubik.solver.feature.editor.EditorViewModel
import com.rubik.solver.feature.scanner.ScannerScreen
import com.rubik.solver.feature.scanner.ScannerViewModel
import com.rubik.solver.feature.solver3d.Solver3DScreen
import com.rubik.solver.feature.solver3d.Solver3DViewModel
import com.rubik.solver.ui.theme.RubikSolverTheme

private object Routes {
    const val SCANNER = "scanner"
    const val EDITOR = "editor"
    const val SOLVER_3D = "solver3d"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RubikSolverTheme {
                val navController = rememberNavController()
                val scannerViewModel: ScannerViewModel = viewModel()
                val editorViewModel: EditorViewModel = viewModel()
                val solver3DViewModel: Solver3DViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = Routes.SCANNER
                ) {
                    composable(Routes.SCANNER) {
                        ScannerScreen(
                            viewModel = scannerViewModel,
                            onProceedToEditor = { scannedState, lowConfidence ->
                                editorViewModel.loadState(scannedState, lowConfidence)
                                navController.navigate(Routes.EDITOR)
                            }
                        )
                    }

                    composable(Routes.EDITOR) {
                        EditorScreen(
                            viewModel = editorViewModel,
                            onBackToScanner = { navController.popBackStack() },
                            onNavigateToSolver3D = { validatedState, solveSuccess ->
                                solver3DViewModel.loadSolution(
                                    initialState = validatedState,
                                    moves = solveSuccess.moves,
                                    solveDurationMs = solveSuccess.solveDurationMs
                                )
                                navController.navigate(Routes.SOLVER_3D)
                            }
                        )
                    }

                    composable(Routes.SOLVER_3D) {
                        Solver3DScreen(
                            viewModel = solver3DViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
