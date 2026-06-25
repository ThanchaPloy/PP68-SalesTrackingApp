package com.pp68.backend.application.di

import com.pp68.backend.data.repository.*
import com.pp68.backend.data.service.FcmService
import com.pp68.backend.domain.usecase.*
import org.koin.dsl.module

val appModule = module {

    // Repositories
    single { EmployeeRepositoryImpl() }
    single { BranchRepositoryImpl() }
    single { CustomerRepositoryImpl() }
    single { ContactPersonRepositoryImpl() }
    single { ProjectRepositoryImpl() }
    single { AppointmentRepositoryImpl() }
    single { ActivityResultRepositoryImpl() }
    single { ActivityMasterRepositoryImpl() }
    single { ChecklistRepositoryImpl() }
    single { ProductRepositoryImpl() }
    single { ProjectMemberRepositoryImpl() }
    single { ProjectContactRepositoryImpl() }
    single { CallLogRepositoryImpl() }

    // Services
    single { FcmService() }

    // Use Cases
    single { AuthUseCase(get()) }
    single { ProjectUseCase(get(), get(), get()) }
    single { AppointmentUseCase(get(), get(), get(), get()) }
}
