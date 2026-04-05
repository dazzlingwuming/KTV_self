import type { ApiEnvelope, CreateOrderRequest, CreateOrderResponse } from '../types/order'

export async function createOrder(payload: CreateOrderRequest): Promise<ApiEnvelope<CreateOrderResponse>> {
  const response = await fetch('/api/order/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return response.json()
}
