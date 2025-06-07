// src/services/mtgApi.ts
import axios from 'axios'
import type { MtgSet, MtgCard, ApiResponse } from '@/types/mtg'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/mtg',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Intercepteur pour la gestion des erreurs
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('Erreur API:', error)
    return Promise.reject(error)
  }
)

// Intercepteur pour logger les requêtes
api.interceptors.request.use(
  (config) => {
    console.log(`📡 API Request: ${config.method?.toUpperCase()} ${config.url}`)
    return config
  }
)

export const mtgApiService = {
  // Récupérer toutes les extensions
  async getAllSets(): Promise<MtgSet[]> {
    const response = await api.get<ApiResponse<MtgSet[]>>('/sets')
    return response.data.data
  },

  // Récupérer la dernière extension
  async getLatestSet(): Promise<MtgSet> {
    const response = await api.get<ApiResponse<MtgSet>>('/sets/latest')
    return response.data.data
  },

  // Récupérer la dernière extension avec ses cartes
  async getLatestSetWithCards(): Promise<MtgSet> {
    const response = await api.get<ApiResponse<MtgSet>>('/sets/latest/cards')
    return response.data.data
  },

  // Récupérer une extension spécifique avec ses cartes - NOUVELLE URL
  async getSetWithCards(setCode: string): Promise<MtgSet> {
    const response = await api.get<ApiResponse<MtgSet>>(`/sets/${setCode}/with-cards`)
    return response.data.data
  },

  // Récupérer une extension spécifique sans cartes
  async getSetByCode(setCode: string): Promise<MtgSet> {
    const response = await api.get<ApiResponse<MtgSet>>(`/sets/${setCode}`)
    return response.data.data
  },

  // Récupérer seulement les cartes d'une extension
  async getCardsFromSet(setCode: string): Promise<MtgCard[]> {
    const response = await api.get<ApiResponse<MtgCard[]>>(`/sets/${setCode}/cards-only`)
    return response.data.data
  }
}
