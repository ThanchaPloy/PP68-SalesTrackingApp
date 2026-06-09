package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.Project
import com.pp68.backend.domain.entity.ProjectContact
import com.pp68.backend.domain.entity.ProjectMember
import com.pp68.backend.domain.repository.ProjectContactRepository
import com.pp68.backend.domain.repository.ProjectMemberRepository
import com.pp68.backend.domain.repository.ProjectRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class CreateProjectRequest(
    val project_id: String,
    val cust_id: String,
    val branch_id: String? = null,
    val billing_branch_id: String? = null,
    val project_name: String,
    val expected_value: Double? = null,
    val project_status: String? = null,
    val start_date: String? = null,
    val closing_date: String? = null,
    val desired_completion_date: String? = null,
    val project_lat: Double? = null,
    val project_long: Double? = null,
    val opportunity_score: String? = null,
    val progress_pct: Int? = null,
    val loss_reason: String? = null,
    val user_id: String? = null
)

fun Route.projectRoutes() {
    val repo: ProjectRepository by inject()
    val memberRepo: ProjectMemberRepository by inject()
    val contactRepo: ProjectContactRepository by inject()

    authenticate("jwt-auth") {

        route("/project") {
            get {
                val projectId = call.request.queryParameters["project_id"]
                val limit     = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    projectId != null && projectId.startsWith("in.") -> {
                        val ids = projectId.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond(repo.findByIds(ids))
                    }
                    projectId != null -> {
                        val p = repo.findById(projectId) ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.respond(listOf(p))
                    }
                    else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "project_id required"))
                }
            }

            post {
                val req = call.receive<CreateProjectRequest>()
                val project = repo.create(Project(
                    req.project_id, req.cust_id, req.branch_id, req.billing_branch_id,
                    req.project_name, req.expected_value, req.project_status, req.start_date,
                    req.closing_date, req.desired_completion_date, req.project_lat, req.project_long,
                    req.opportunity_score, req.progress_pct, req.loss_reason, req.user_id, null, null
                ))
                call.respond(HttpStatusCode.Created, listOf(project))
            }

            patch {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                val updated = repo.update(projectId, updates) ?: return@patch call.respond(HttpStatusCode.NotFound)
                call.respond(listOf(updated))
            }

            delete {
                val projectId = call.request.queryParameters["project_id"]
                val custId    = call.request.queryParameters["cust_id"]
                when {
                    projectId != null -> if (repo.delete(projectId)) call.respond(HttpStatusCode.NoContent)
                                         else call.respond(HttpStatusCode.NotFound)
                    custId != null    -> { repo.deleteByCustId(custId); call.respond(HttpStatusCode.NoContent) }
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        route("/project_sales_member") {
            get {
                val projectId = call.request.queryParameters["project_id"]
                val userId    = call.request.queryParameters["user_id"]
                when {
                    projectId != null -> call.respond(memberRepo.findByProjectId(projectId))
                    userId    != null -> call.respond(memberRepo.findByUserId(userId))
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
            post {
                val members = call.receive<List<ProjectMember>>()
                call.respond(HttpStatusCode.Created, memberRepo.addMembers(members))
            }
            delete {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                memberRepo.deleteByProjectId(projectId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/project_contact") {
            get {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond(contactRepo.findByProjectId(projectId))
            }
            post {
                val contacts = call.receive<List<ProjectContact>>()
                contactRepo.addContacts(contacts)
                call.respond(HttpStatusCode.Created)
            }
            delete {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                contactRepo.deleteByProjectId(projectId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}