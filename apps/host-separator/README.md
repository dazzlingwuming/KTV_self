# Host Separator

独立主机分离服务。

职责固定为：

- 接收机顶盒上传的原始音频文件
- 调用仓库内置 `inst_v1e` 模型做伴奏/人声分离
- 返回伴奏和人声结果文件

## 目录

```text
apps/host-separator/
  app.py
  config.py
  storage.py
  tasks.py
  separator_engine.py
  engine/
    model_inst_v1e.py
    modules/
      bs_roformer/
  configs/
    inst_v1e.ckpt.yaml
  models/
    inst_v1e.ckpt
  storage/
```

## 启动

```powershell
cd apps/host-separator
pip install -r requirements.txt
python app.py
```

默认监听：

- `0.0.0.0:9090`

## 环境变量

- `HOST_SEPARATOR_HOST`
- `HOST_SEPARATOR_PORT`
- `HOST_SEPARATOR_STORAGE_ROOT`
- `HOST_SEPARATOR_ENGINE_ROOT`
- `HOST_SEPARATOR_MODEL_WEIGHTS`
- `HOST_SEPARATOR_MODEL_CONFIG`
- `HOST_SEPARATOR_DEVICE`
  - `auto` / `cpu` / `cuda`
- `HOST_SEPARATOR_CHUNK_DURATION_SEC`
  - 默认 `20`
- `HOST_SEPARATOR_CHUNK_OVERLAP_SEC`
  - 默认 `2`

默认不依赖外部 `D:\github\Vocal_Remover`

## 接口

### `GET /api/ping`

健康检查。

### `POST /api/separate/create`

`multipart/form-data`

- `audio_file`
- `song_id`
- `source_type`
- `source_id`

### `GET /api/separate/status?task_id=...`

查询任务状态。

### `GET /api/separate/result?task_id=...`

查询结果地址。

### `GET /api/separate/file/{task_id}/accompaniment`

下载伴奏文件。

### `GET /api/separate/file/{task_id}/vocal`

下载人声文件。
