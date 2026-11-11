package com.rubik.solver

import android.app.Application
import com.rubik.solver.core.cv.OpenCvInitializer
import com.rubik.solver.core.solver.KociembaRubikSolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RubikSolverApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        OpenCvInitializer.init()
        appScope.launch {
            KociembaRubikSolver().warmUp()
        }
    }
}
