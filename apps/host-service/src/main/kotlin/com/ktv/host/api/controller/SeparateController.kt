package com.ktv.host.api.controller

import com.ktv.host.api.dto.CreateSeparateTaskResponse
import com.ktv.host.api.dto.GetSeparateResultResponse
import com.ktv.host.api.dto.GetSeparateStatusResponse
import com.ktv.host.common.HostApiResult
import com.ktv.host.task.SeparateTaskManager
import com.ktv.host.upload.MultipartUploadHandler
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class SeparateController(
    private val taskManager: SeparateTaskManager,
    private val uploadHandler: MultipartUploadHandler,
) {
    fun register(route: Route) {
        route("/api/separate") {
            post("/create") {
                val songId = call.request.queryParameters["song_id"] ?: "unknown-song"
                val task = taskManager.createPendingTask(songId)
                uploadHandler.receive(call, songId)
                taskManager.markMockCompleted(task.taskId)
                call.respond(
                    HostApiResult(
                        data = CreateSeparateTaskResponse(task.taskId, "success"),
                    ),
                )
            }

            get("/status") {
                val taskId = call.request.queryParameters["task_id"].orEmpty()
                val task = taskManager.find(taskId)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound, HostApiResult<Any>(code = 404, message = "task not found"))
                } else {
                    call.respond(
                        HostApiResult(
                            data = GetSeparateStatusResponse(
                                taskId = task.taskId,
                                status = task.status,
                                progress = task.progress,
                            ),
                        ),
                    )
                }
            }

            get("/result") {
                val taskId = call.request.queryParameters["task_id"].orEmpty()
                val task = taskManager.find(taskId)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound, HostApiResult<Any>(code = 404, message = "task not found"))
                } else {
                    call.respond(
                        HostApiResult(
                            data = GetSeparateResultResponse(
                                taskId = task.taskId,
                                status = task.status,
                                accompanimentUrl = task.accompanimentPath,
                                vocalUrl = task.vocalPath,
                            ),
                        ),
                    )
                }
            }
        }
    }
}
