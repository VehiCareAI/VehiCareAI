package com.example.vehicare.di

import com.example.vehicare.domain.diagnostic.bayesian.BayesianDiagnosticEngine
import com.example.vehicare.domain.diagnostic.bayesian.DiagnosticEngineConfig
import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.diagnostic.knowledgebase.KnowledgeBase
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.safety.RuleBasedSafetyEvaluator
import com.example.vehicare.domain.safety.SafetyEvaluator
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.domain.usecase.EvaluateSymptomsUseCase
import com.example.vehicare.domain.usecase.LoadAssessmentAnalysisUseCase
import com.example.vehicare.domain.usecase.QuestionnaireEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the pure-Kotlin research core. The engine and knowledge base are singletons because
 * loading and validating the knowledge base is done once (Section 6.2: validation fails loudly at
 * load time, so it must not be repeated per screen).
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideKnowledgeBase(): KnowledgeBase = VehiCareKnowledgeBase.instance

    @Provides
    @Singleton
    fun provideSafetyEvaluator(): SafetyEvaluator = RuleBasedSafetyEvaluator()

    @Provides
    @Singleton
    fun provideDiagnosticEngine(
        knowledgeBase: KnowledgeBase,
        safetyEvaluator: SafetyEvaluator
    ): BayesianDiagnosticEngine = NaiveBayesDiagnosticEngine(
        knowledgeBase = knowledgeBase,
        safetyEvaluator = safetyEvaluator,
        config = DiagnosticEngineConfig()
    )

    @Provides
    @Singleton
    fun provideQuestionnaireEngine(): QuestionnaireEngine = QuestionnaireEngine()

    @Provides
    @Singleton
    fun provideEvaluateSymptomsUseCase(engine: BayesianDiagnosticEngine): EvaluateSymptomsUseCase =
        EvaluateSymptomsUseCase(engine)

    @Provides
    @Singleton
    fun provideLoadAssessmentAnalysisUseCase(
        assessmentRepository: AssessmentRepository,
        vehicleRepository: VehicleRepository,
        engine: BayesianDiagnosticEngine,
        knowledgeBase: KnowledgeBase
    ): LoadAssessmentAnalysisUseCase = LoadAssessmentAnalysisUseCase(
        assessmentRepository = assessmentRepository,
        vehicleRepository = vehicleRepository,
        engine = engine,
        knowledgeBase = knowledgeBase
    )
}
