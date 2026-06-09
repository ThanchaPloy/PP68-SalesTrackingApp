package com.pp68.backend.application.di

import com.pp68.backend.data.repository.*
import com.pp68.backend.domain.repository.*
import org.koin.dsl.module

val appModule = module {

    // Repositories
    single<UserRepository>           { UserRepositoryImpl() }
    single<BranchRepository>         { BranchRepositoryImpl() }
    single<CustomerRepository>       { CustomerRepositoryImpl() }
    single<ContactPersonRepository>  { ContactPersonRepositoryImpl() }
    single<ProjectRepository>        { ProjectRepositoryImpl() }
    single<AppointmentRepository>    { AppointmentRepositoryImpl() }
    single<ActivityResultRepository> { ActivityResultRepositoryImpl() }
    single<ActivityMasterRepository> { ActivityMasterRepositoryImpl() }
    single<ChecklistRepository>      { ChecklistRepositoryImpl() }
    single<ProductRepository>        { ProductRepositoryImpl() }
    single<ProjectMemberRepository>  { ProjectMemberRepositoryImpl() }
    single<ProjectContactRepository> { ProjectContactRepositoryImpl() }
    single<CallLogRepository>        { CallLogRepositoryImpl() }
}