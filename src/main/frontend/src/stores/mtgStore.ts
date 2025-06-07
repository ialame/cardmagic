// src/stores/mtgStore.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MtgSet, MtgCard } from '@/types/mtg'
import { mtgApiService } from '@/services/mtgApi'

export const useMtgStore = defineStore('mtg', () => {
  // State
  const latestSet = ref<MtgSet | null>(null)
  const sets = ref<MtgSet[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const hasLatestSet = computed(() => latestSet.value !== null)
  const latestSetCards = computed(() => latestSet.value?.cards || [])
  const setsCount = computed(() => sets.value.length)

  const cardsByRarity = computed(() => {
    const cards = latestSetCards.value
    return cards.reduce((acc, card) => {
      const rarity = card.rarity || 'Unknown'
      if (!acc[rarity]) acc[rarity] = []
      acc[rarity].push(card)
      return acc
    }, {} as Record<string, MtgCard[]>)
  })

  const totalCards = computed(() => latestSetCards.value.length)

  // Actions
  const fetchLatestSetWithCards = async (): Promise<void> => {
    loading.value = true
    error.value = null

    try {
      console.log('🔍 Récupération de la dernière extension...')
      latestSet.value = await mtgApiService.getLatestSetWithCards()
      console.log('✅ Extension récupérée:', latestSet.value?.name)
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || 'Erreur lors du chargement de la dernière extension'
      error.value = errorMessage
      console.error('❌ Erreur fetchLatestSetWithCards:', err)
    } finally {
      loading.value = false
    }
  }

  const fetchAllSets = async (): Promise<void> => {
    loading.value = true
    error.value = null

    try {
      console.log('🔍 Récupération de toutes les extensions...')
      sets.value = await mtgApiService.getAllSets()
      console.log('✅ Extensions récupérées:', sets.value.length)
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || 'Erreur lors du chargement des extensions'
      error.value = errorMessage
      console.error('❌ Erreur fetchAllSets:', err)
    } finally {
      loading.value = false
    }
  }

  const fetchCardsFromSet = async (setCode: string): Promise<MtgCard[]> => {
    loading.value = true
    error.value = null

    try {
      console.log('🔍 Récupération des cartes pour:', setCode)
      const cards = await mtgApiService.getCardsFromSet(setCode)
      console.log('✅ Cartes récupérées:', cards.length)
      return cards
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || `Erreur lors du chargement des cartes de l'extension ${setCode}`
      error.value = errorMessage
      console.error('❌ Erreur fetchCardsFromSet:', err)
      return []
    } finally {
      loading.value = false
    }
  }

  const clearError = (): void => {
    error.value = null
  }

  const reset = (): void => {
    latestSet.value = null
    sets.value = []
    loading.value = false
    error.value = null
  }

  // Ajoutez ces nouvelles actions dans le store
  const fetchSetByCode = async (setCode: string): Promise<void> => {
    loading.value = true
    error.value = null

    try {
      console.log('🔍 Récupération de l\'extension:', setCode)
      latestSet.value = await mtgApiService.getSetWithCards(setCode)
      console.log('✅ Extension récupérée:', latestSet.value?.name)
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || `Erreur lors du chargement de l'extension ${setCode}`
      error.value = errorMessage
      console.error('❌ Erreur fetchSetByCode:', err)
    } finally {
      loading.value = false
    }
  }

  const fetchAllSetsOnly = async (): Promise<MtgSet[]> => {
    try {
      console.log('🔍 Récupération de toutes les extensions...')
      const allSets = await mtgApiService.getAllSets()
      sets.value = allSets
      console.log('✅ Extensions récupérées:', allSets.length)
      return allSets
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || 'Erreur lors du chargement des extensions'
      error.value = errorMessage
      console.error('❌ Erreur fetchAllSetsOnly:', err)
      return []
    }
  }

  return {
    // State
    latestSet,
    sets,
    loading,
    error,
    // Getters
    hasLatestSet,
    latestSetCards,
    setsCount,
    cardsByRarity,
    totalCards,
    // Actions
    fetchLatestSetWithCards,
    fetchAllSets,
    fetchCardsFromSet,
    clearError,
    reset,
    fetchSetByCode,
    fetchAllSetsOnly,
  }
})
