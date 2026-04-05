from __future__ import annotations

import tempfile
from pathlib import Path

from flask import Flask, jsonify, request, send_file

from config import load_config
from separator_engine import VocalRemoverSeparatorEngine
from storage import HostStoragePaths
from tasks import SeparateTaskManager

config = load_config()
storage_paths = HostStoragePaths(config.storage_root)
engine = VocalRemoverSeparatorEngine(config)
task_manager = SeparateTaskManager(storage_paths, engine)

app = Flask(__name__)


def api_result(code: int = 0, message: str = "ok", **data):
    payload = {"code": code, "message": message}
    payload.update(data)
    return jsonify(payload)


@app.get("/api/ping")
def ping():
    return api_result(service="host-separator", status="ok")


@app.post("/api/separate/create")
def create_task():
    audio_file = request.files.get("audio_file")
    if audio_file is None or not audio_file.filename:
        return api_result(code=400, message="audio_file is required"), 400

    song_id = request.form.get("song_id", "").strip()
    source_type = request.form.get("source_type", "").strip()
    source_id = request.form.get("source_id", "").strip()
    if not song_id:
        return api_result(code=400, message="song_id is required"), 400

    suffix = Path(audio_file.filename).suffix or ".bin"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp:
        audio_file.save(temp.name)
        temp_path = Path(temp.name)

    task = task_manager.create_task(
        song_id=song_id,
        source_type=source_type or "bilibili",
        source_id=source_id,
        filename=audio_file.filename,
        input_file_path=temp_path,
    )
    return api_result(
        task_id=task.task_id,
        status=task.status,
        progress=task.progress,
    )


@app.get("/api/separate/status")
def get_status():
    task_id = request.args.get("task_id", "").strip()
    task = task_manager.get_task(task_id)
    if task is None:
        return api_result(code=404, message="task not found"), 404
    return api_result(
        task_id=task.task_id,
        status=task.status,
        progress=task.progress,
        error_message=task.error_message,
        song_id=task.song_id,
        source_type=task.source_type,
        source_id=task.source_id,
    )


@app.get("/api/separate/result")
def get_result():
    task_id = request.args.get("task_id", "").strip()
    task = task_manager.get_task(task_id)
    if task is None:
        return api_result(code=404, message="task not found"), 404
    return api_result(
        task_id=task.task_id,
        status=task.status,
        progress=task.progress,
        error_message=task.error_message,
        accompaniment_url=f"/api/separate/file/{task.task_id}/accompaniment" if task.accompaniment_path else "",
        vocal_url=f"/api/separate/file/{task.task_id}/vocal" if task.vocal_path else "",
        sample_rate=task.sample_rate,
    )


@app.get("/api/separate/file/<task_id>/<kind>")
def get_result_file(task_id: str, kind: str):
    task = task_manager.get_task(task_id)
    if task is None:
        return api_result(code=404, message="task not found"), 404

    if kind == "accompaniment":
        file_path = task.accompaniment_path
    elif kind == "vocal":
        file_path = task.vocal_path
    else:
        return api_result(code=400, message="unsupported kind"), 400

    path = Path(file_path)
    if not file_path or not path.exists():
        return api_result(code=404, message="result file not found"), 404
    return send_file(path, as_attachment=True, download_name=path.name)


if __name__ == "__main__":
    app.run(host=config.host, port=config.port, debug=False)
