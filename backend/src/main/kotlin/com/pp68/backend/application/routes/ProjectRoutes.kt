package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.Project
import com.pp68.backend.domain.entity.ProjectContact
import com.pp68.backend.domain.entity.ProjectMember
import com.pp68.backend.domain.usecase.ProjectUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.projectRoutes() {
    val projectUseCase: ProjectUseCase by inject()

    authenticate("jwt-auth") {

        route("/project") {
            get {
                val projectIdRaw = call.request.queryParameters["project_id"]

                when {
                    projectIdRaw != null && projectIdRaw.startsWith("in.") -> {
                        val ids = projectIdRaw.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond<List<Project>>(projectUseCase.findByIds(ids))
                    }
                    projectIdRaw != null -> call.respond<List<Project>>(listOf(projectUseCase.findById(projectIdRaw.stripEq()!!)))
                    else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "project_id required"))
                }
            }

            post {
                val project = projectUseCase.create(call.receive<Project>())
                call.respond<List<Project>>(HttpStatusCode.Created, listOf(project))
            }

            patch {
                val projectId = call.request.queryParameters["project_id"].stripEq()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                call.respond<List<Project>>(listOf(projectUseCase.update(projectId, updates)))
            }

            delete {
                val projectId = call.request.queryParameters["project_id"].stripEq()
                val custId    = call.request.queryParameters["cust_id"].stripEq()
                when {
                    projectId != null -> { projectUseCase.delete(projectId); call.respond(HttpStatusCode.NoContent) }
                    custId    != null -> { projectUseCase.deleteByCustId(custId); call.respond(HttpStatusCode.NoContent) }
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        route("/project_sales_member") {
            get {
                val projectId = call.request.queryParameters["project_id"].stripEq()
                val userId    = call.request.queryParameters["user_id"].stripEq()
                when {
                    projectId != null -> call.respond<List<ProjectMember>>(projectUseCase.getMembersByProjectId(projectId))
                    userId    != null -> call.respond<List<ProjectMember>>(projectUseCase.getMembersByUserId(userId))
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
            post {
                val members = call.receive<List<ProjectMember>>()
                call.respond<List<ProjectMember>>(HttpStatusCode.Created, projectUseCase.addMembers(members))
            }
            delete {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                projectUseCase.deleteMembersByProjectId(projectId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/project_contact") {
            get {
                val projectId = call.request.queryParameters["project_id"].stripEq()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond<List<ProjectContact>>(projectUseCase.getContactsByProjectId(projectId))
            }
            post {
                val contacts = call.receive<List<ProjectContact>>()
                projectUseCase.addContacts(contacts)
                call.respond(HttpStatusCode.Created)
            }
            delete {
                val projectId = call.request.queryParameters["project_id"].stripEq()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                projectUseCase.deleteContactsByProjectId(projectId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}