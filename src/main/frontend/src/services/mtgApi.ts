// src/services/mtgApi.ts - Version corrigée

import axios from 'axios'
import type { MtgSet, MtgCard, ApiResponse } from '@/types/mtg'

const api = axios.create({
  baseURL: '/api/mtg',
  timeout: 30000,
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

  // CORRECTION: Récupérer la dernière extension avec ses cartes
  async getLatestSetWithCards(): Promise<MtgSet> {
    const response = await api.get<ApiResponse<MtgSet>>('/sets/latest/cards')
    return response.data.data
  },

  // Récupérer une extension spécifique avec ses cartes
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
    const response = await api.get<ApiResponse<MtgCard[]>>(`/sets/${setCode}/cards`)
    return response.data.data
  }
}

// Utilitaires
export const mtgUtils = {
  formatDate(dateString: string): string {
    try {
      return new Date(dateString).toLocaleDateString('fr-FR')
    } catch {
      return dateString
    }
  },

  formatPercentage(value: number): string {
    return `${Math.round(value)}%`
  },

  getRarityColor(rarity: string): string {
    const colors: Record<string, string> = {
      'Common': '#1a1a1a',
      'Uncommon': '#c0c0c0',
      'Rare': '#ffd700',
      'Mythic Rare': '#ff4500',
      'Special': '#800080',
      'Basic Land': '#228b22'
    }
    return colors[rarity] || '#666666'
  }
}
