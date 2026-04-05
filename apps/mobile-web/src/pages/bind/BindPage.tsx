import { useEffect, useRef, useState } from 'react'
import { createOrder } from '../../api/orderApi'
import { fetchLocalFileStatus, fetchLocalList, localDelete, localOrder } from '../../api/localApi'
import { fetchPlayerStatus, pausePlayer, playNext, resumePlayer, setPlayerVolume } from '../../api/playerApi'
import { fetchQueue, moveQueueItemNext, removeQueueItem } from '../../api/queueApi'
import { searchSongs } from '../../api/searchApi'
import { bindSession, fetchSessionStatus } from '../../api/sessionApi'
import { sessionStore } from '../../state/sessionStore'
import { WsClient } from '../../ws/wsClient'
import type { QueueItemDto } from '../../types/queue'
import type { SearchSongItem } from '../../types/search'

export function BindPage() {
  const wsRef = useRef<WsClient | null>(null)
  const [output, setOutput] = useState('')
  const [sessionId, setSessionId] = useState(sessionStore.get().sessionId)
  const [clientName, setClientName] = useState(sessionStore.get().clientName || defaultClientName())
  const [clientId] = useState(getOrCreateClientId())
  const [keyword, setKeyword] = useState('')
  const [results, setResults] = useState<SearchSongItem[]>([])
  const [queue, setQueue] = useState<QueueItemDto[]>([])
  const [connected, setConnected] = useState(false)
  const [lastDownloadEvent, setLastDownloadEvent] = useState<any>(null)
  const [playerStatus, setPlayerStatus] = useState<any>(null)
  const [fileStatusOutput, setFileStatusOutput] = useState('')
  const [localList, setLocalList] = useState<any[]>([])

  async function refresh() {
    const response = await fetchSessionStatus()
    sessionStore.set({
      sessionId: response.data?.session_id ?? '',
      bindStatus: response.data?.bind_status ?? 'unbound',
      deviceName: response.data?.device_name ?? '',
      baseUrl: window.location.origin,
      clientId,
      clientName,
      clientCount: response.data?.client_count ?? 0,
    })
    const queueResponse = await fetchQueue()
    setQueue(queueResponse.data?.items ?? [])
    const playerResponse = await fetchPlayerStatus()
    setPlayerStatus(playerResponse.data)
    const localResponse = await fetchLocalList()
    setLocalList(localResponse.data?.list ?? [])
    setSessionId(response.data?.session_id ?? '')
    setOutput(JSON.stringify(response, null, 2))
    if (response.data?.session_id) {
      connectWs(response.data.session_id)
    }
  }

  async function bind() {
    const response = await bindSession({
      session_id: sessionId,
      client_id: clientId,
      client_name: clientName,
      sign: '',
    })
    setOutput(JSON.stringify(response, null, 2))
    connectWs(response.data?.session_id ?? sessionId)
    refresh()
  }

  async function search() {
    const response = await searchSongs(keyword)
    setResults(response.data?.list ?? [])
  }

  async function order(song: SearchSongItem) {
    const response = await createOrder({
      client_id: clientId,
      client_name: clientName,
      source_type: song.source_type,
      source_id: song.source_id,
      title: song.title,
      artist: song.artist,
      duration: song.duration,
      cover_url: song.cover_url,
    })
    setOutput(JSON.stringify(response, null, 2))
    refresh()
  }

  async function removeItem(queueId: string) {
    await removeQueueItem({ queue_id: queueId })
    refresh()
  }

  async function moveNext(queueId: string) {
    await moveQueueItemNext({ queue_id: queueId })
    refresh()
  }

  function connectWs(currentSessionId: string) {
    wsRef.current?.disconnect()
    const client = new WsClient()
    wsRef.current = client
    client.connect(
      { sessionId: currentSessionId, clientId, clientName },
      {
        onOpen: () => setConnected(true),
        onClose: () => setConnected(false),
        onMessage: async (payload: any) => {
          if (payload?.event === 'download_updated') {
            setLastDownloadEvent(payload.data)
          }
          if (payload?.event === 'player_updated') {
            setPlayerStatus(payload.data)
          }
          const queueResponse = await fetchQueue()
          setQueue(queueResponse.data?.items ?? [])
          const localResponse = await fetchLocalList()
          setLocalList(localResponse.data?.list ?? [])
        },
      },
    )
  }

  async function checkFileStatus(songId: string) {
    const response = await fetchLocalFileStatus(songId)
    setFileStatusOutput(JSON.stringify(response, null, 2))
  }

  async function nextSong() {
    await playNext()
    const playerResponse = await fetchPlayerStatus()
    setPlayerStatus(playerResponse.data)
    refresh()
  }

  async function pauseSong() {
    await pausePlayer()
    const playerResponse = await fetchPlayerStatus()
    setPlayerStatus(playerResponse.data)
  }

  async function resumeSong() {
    await resumePlayer()
    const playerResponse = await fetchPlayerStatus()
    setPlayerStatus(playerResponse.data)
  }

  async function changeVolume(value: number) {
    await setPlayerVolume(value)
    const playerResponse = await fetchPlayerStatus()
    setPlayerStatus(playerResponse.data)
  }

  async function orderLocal(songId: string) {
    await localOrder({ song_id: songId, client_id: clientId, client_name: clientName })
    refresh()
  }

  async function deleteLocal(songId: string) {
    await localDelete({ song_id: songId })
    refresh()
  }

  useEffect(() => {
    refresh()
    return () => wsRef.current?.disconnect()
  }, [])

  return (
    <main style={{ maxWidth: 680, margin: '40px auto', padding: 24, fontFamily: 'sans-serif' }}>
      <h1>家庭 KTV 控制页</h1>
      <p>首期仅验证机顶盒会话绑定。</p>
      <label htmlFor="sessionId">Session ID</label>
      <input
        id="sessionId"
        value={sessionId}
        onChange={(e) => setSessionId(e.target.value)}
        style={{ width: '100%', padding: 10, marginTop: 8, marginBottom: 12 }}
      />
      <label htmlFor="clientName">客户端名称</label>
      <input
        id="clientName"
        value={clientName}
        onChange={(e) => setClientName(e.target.value)}
        style={{ width: '100%', padding: 10, marginTop: 8, marginBottom: 12 }}
      />
      <p>Client ID: {clientId}</p>
      <p>WebSocket: {connected ? 'connected' : 'disconnected'}</p>
      <div>
        <button onClick={refresh}>刷新状态</button>
        <button onClick={bind} style={{ marginLeft: 12 }}>绑定</button>
        <button onClick={nextSong} style={{ marginLeft: 12 }}>下一首</button>
        <button onClick={pauseSong} style={{ marginLeft: 12 }}>暂停</button>
        <button onClick={resumeSong} style={{ marginLeft: 12 }}>继续</button>
      </div>
      {playerStatus && (
        <div style={{ marginTop: 24, background: '#f0f6ef', padding: 12 }}>
          <h3>当前播放</h3>
          <div>{playerStatus.title || '-'}</div>
          <div>{playerStatus.play_status}</div>
          <div>音量: {playerStatus.volume}</div>
          <div>{playerStatus.error_message || '-'}</div>
          <input
            type="range"
            min={0}
            max={100}
            value={playerStatus.volume ?? 100}
            onChange={(e) => changeVolume(Number(e.target.value))}
          />
        </div>
      )}
      <div style={{ marginTop: 24 }}>
        <label htmlFor="keyword">搜索</label>
        <input
          id="keyword"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: '100%', padding: 10, marginTop: 8, marginBottom: 12 }}
        />
        <button onClick={search}>搜索歌曲</button>
        <ul>
          {results.map((song) => (
            <li key={song.source_id} style={{ marginTop: 12 }}>
              {song.title} - {song.artist ?? 'Unknown'}
              <button onClick={() => order(song)} style={{ marginLeft: 12 }}>点歌</button>
            </li>
          ))}
        </ul>
      </div>
      <div style={{ marginTop: 24 }}>
        <h3>公共队列</h3>
        <ul>
          {queue.map((item) => (
            <li key={item.queue_id} style={{ marginTop: 12 }}>
              #{item.position} {item.title} / {item.ordered_by_client_name ?? item.ordered_by_client_id} / {item.download_status} / {item.queue_status} / {item.skip_reason ?? item.error_message ?? '-'}
              <button onClick={() => moveNext(item.queue_id)} style={{ marginLeft: 12 }}>下一首</button>
              <button onClick={() => removeItem(item.queue_id)} style={{ marginLeft: 12 }}>删除</button>
              <button onClick={() => checkFileStatus(item.song_id)} style={{ marginLeft: 12 }}>file-status</button>
            </li>
          ))}
        </ul>
      </div>
      <div style={{ marginTop: 24 }}>
        <h3>本地资源</h3>
        <ul>
          {localList.map((item) => (
            <li key={item.song_id} style={{ marginTop: 12 }}>
              {item.title} / {item.download_status} / {String(item.is_valid)} / {item.file_size}
              <button onClick={() => orderLocal(item.song_id)} style={{ marginLeft: 12 }}>本地点歌</button>
              <button onClick={() => deleteLocal(item.song_id)} style={{ marginLeft: 12 }}>删除缓存</button>
            </li>
          ))}
        </ul>
      </div>
      {lastDownloadEvent && (
        <div style={{ marginTop: 24, background: '#f7f7f7', padding: 12 }}>
          <h3>最近下载事件</h3>
          <div>{lastDownloadEvent.title}</div>
          <div>{lastDownloadEvent.download_status}</div>
          <div>{lastDownloadEvent.error_code || '-'}</div>
          <div>{lastDownloadEvent.error_message || '-'}</div>
        </div>
      )}
      {fileStatusOutput && (
        <pre style={{ background: '#eef3f6', padding: 16, marginTop: 16 }}>{fileStatusOutput}</pre>
      )}
      <pre style={{ background: '#f2f2f2', padding: 16, marginTop: 16 }}>{output}</pre>
    </main>
  )
}

function getOrCreateClientId(): string {
  const existing = window.localStorage.getItem('ktv_client_id')
  if (existing) return existing
  const created = globalThis.crypto?.randomUUID?.() ?? `client-${Date.now()}`
  window.localStorage.setItem('ktv_client_id', created)
  return created
}

function defaultClientName(): string {
  const existing = window.localStorage.getItem('ktv_client_name')
  if (existing) return existing
  const created = `Phone-${Math.floor(Math.random() * 1000)}`
  window.localStorage.setItem('ktv_client_name', created)
  return created
}
