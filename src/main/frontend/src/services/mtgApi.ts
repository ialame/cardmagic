// src/services/mtgApi.ts - Version améliorée
import axios from 'axios'
import type { MtgSet, MtgCard, ApiResponse } from '@/types/mtg'

const api = axios.create({
  baseURL: '/api/mtg',
  timeout: 30000, // Augmenté pour les opérations longues
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
  // ========== MÉTHODES EXISTANTES ==========

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
    const response = await api.get<ApiResponse<MtgCard[]>>(`/sets/${setCode}/cards-only`)
    return response.data.data
  },

  // ========== NOUVELLES MÉTHODES POUR LA SAUVEGARDE ==========

  // Charger les cartes d'une extension (en mémoire seulement)
  async loadSetCards(setCode: string): Promise<{
    set: MtgSet,
    cards: MtgCard[],
    message: string
  }> {
    try {
      console.log(`📥 Chargement des cartes pour ${setCode}...`)

      const response = await api.get<ApiResponse<MtgSet>>(`/sets/${setCode}/with-cards`)

      return {
        set: response.data.data,
        cards: response.data.data.cards || [],
        message: `${response.data.data.cards?.length || 0} cartes chargées pour ${setCode}`
      }
    } catch (error) {
      console.error(`❌ Erreur chargement ${setCode}:`, error)
      throw error
    }
  },

  // Sauvegarder une extension en base de données
  async saveSetToDatabase(setCode: string): Promise<{
    success: boolean,
    message: string,
    cardsCount: number
  }> {
    try {
      console.log(`💾 Sauvegarde en base pour ${setCode}...`)

      const response = await api.post(`/admin/sync-set/${setCode}`)

      return {
        success: response.data.success,
        message: response.data.message,
        cardsCount: 0 // Sera mis à jour par le statut
      }
    } catch (error) {
      console.error(`❌ Erreur sauvegarde ${setCode}:`, error)
      throw error
    }
  },

  // Télécharger les images d'une extension
  async downloadSetImages(setCode: string): Promise<{
    success: boolean,
    message: string
  }> {
    try {
      console.log(`📸 Téléchargement images pour ${setCode}...`)

      const response = await axios.post(`/api/images/download-set/${setCode}`)

      return {
        success: response.status === 202,
        message: `Téléchargement des images de ${setCode} démarré`
      }
    } catch (error) {
      console.error(`❌ Erreur téléchargement images ${setCode}:`, error)
      throw error
    }
  },

  // Sauvegarde complète (base + images)
  async saveCompleteSet(setCode: string): Promise<{
    success: boolean,
    message: string
  }> {
    try {
      console.log(`💽 Sauvegarde complète pour ${setCode}...`)

      const response = await api.post(`/admin/save-complete/${setCode}`)

      return {
        success: response.data.success,
        message: response.data.message
      }
    } catch (error) {
      console.error(`❌ Erreur sauvegarde complète ${setCode}:`, error)
      throw error
    }
  },

  // Obtenir le statut détaillé d'une extension
  async getSetStatus(setCode: string): Promise<{
    code: string,
    name: string,
    type: string,
    releaseDate: string,
    cardsSynced: boolean,
    cardsCount: number,
    imagesDownloaded: number,
    imagesPercentage: number,
    rarityStats: Record<string, number>,
    lastSyncAt?: string
  }> {
    try {
      const response = await api.get(`/admin/set-status/${setCode}`)
      return response.data.data
    } catch (error) {
      console.error(`❌ Erreur statut ${setCode}:`, error)
      throw error
    }
  },

  // Obtenir le statut de toutes les extensions
  async getAllSetsStatus(): Promise<Array<{
    code: string,
    name: string,
    type: string,
    releaseDate: string,
    cardsSynced: boolean,
    cardsCount: number,
    imagesCount: number,
    completionPercentage: number
  }>> {
    try {
      const response = await api.get('/admin/all-sets-status')
      return response.data.data
    } catch (error) {
      console.error('❌ Erreur statut toutes extensions:', error)
      throw error
    }
  },

  // ========== MÉTHODES DE DEBUG ==========

  // Debug: toutes les extensions
  async debugAllSets(): Promise<any[]> {
    const response = await api.get('/debug/all-sets')
    return response.data.data
  },

  // Debug: détection dernière extension
  async debugLatestSetDetection(): Promise<any> {
    const response = await api.get('/debug/latest-set-detection')
    return response.data.data
  },

  // Forcer la synchronisation temps réel
  async forceSyncRealtime(setCode: string): Promise<{
    success: boolean,
    message: string
  }> {
    try {
      const response = await api.post(`/admin/force-sync-realtime/${setCode}`)
      return {
        success: response.data.success,
        message: response.data.message
      }
    } catch (error) {
      console.error(`❌ Erreur sync temps réel ${setCode}:`, error)
      throw error
    }
  },

  // ========== STATISTIQUES ==========

  // Statistiques de téléchargement d'images
  async getImageStats(): Promise<{
    totalCards: number,
    downloadedCards: number,
    pendingCards: number,
    downloadPercentage: number
  }> {
    try {
      const response = await axios.get('/api/images/stats')
      return response.data
    } catch (error) {
      console.error('❌ Erreur stats images:', error)
      throw error
    }
  },

  // Statistiques admin générales
  async getAdminStats(): Promise<{
    totalCards: number,
    totalSets: number,
    syncedSets: number,
    distinctArtists: number,
    imageStats: any
  }> {
    try {
      const response = await api.get('/admin/stats')
      return response.data.data
    } catch (error) {
      console.error('❌ Erreur stats admin:', error)
      throw error
    }
  }
}

// ========== UTILITAIRES ==========

export const mtgUtils = {
  // Formater la date
  formatDate(dateString: string): string {
    try {
      return new Date(dateString).toLocaleDateString('fr-FR')
    } catch {
      return dateString
    }
  },

  // Formater le pourcentage
  formatPercentage(value: number): string {
    return `${Math.round(value)}%`
  },

  // Obtenir la couleur de rareté
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
  },

  // Obtenir l'icône de type d'extension
  getSetTypeIcon(type: string): string {
    const icons: Record<string, string> = {
      'expansion': '🎯',
      'core': '⭐',
      'draft_innovation': '🔮',
      'commander': '👑',
      'masters': '🏆',
      'memorabilia': '🎨',
      'promo': '✨'
    }
    return icons[type] || '📦'
  }
}
