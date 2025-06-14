<template>
  <div class="set-selector">
    <div class="selector-header">
      <h3>🎴 Choisir une extension</h3>
      <button @click="toggleSelector" class="toggle-button">
        {{ showSelector ? 'Masquer' : 'Afficher' }} les extensions
      </button>
    </div>

    <div v-if="showSelector" class="selector-content">
      <!-- Recherche d'extensions -->
      <div class="search-sets">
        <input
          v-model="searchTerm"
          placeholder="Rechercher une extension..."
          class="search-input"
        />
      </div>

      <!-- Filtres -->
      <div class="filters">
        <select v-model="selectedType" class="filter-select">
          <option value="">Tous les types</option>
          <option v-for="type in availableTypes" :key="type" :value="type">
            {{ type }}
          </option>
        </select>

        <select v-model="sortBy" class="filter-select">
          <option value="releaseDate">Trier par date</option>
          <option value="name">Trier par nom</option>
          <option value="code">Trier par code</option>
        </select>
      </div>

      <!-- Liste des extensions -->
      <div class="sets-grid">
        <div
          v-for="set in filteredAndSortedSets"
          :key="set.code"
          class="set-item"
          :class="{
            'selected': selectedSet?.code === set.code,
            'has-cards': set.cardsCount > 0,
            'synced': set.cardsSynced
          }"
          @click="selectSet(set)"
        >
          <div class="set-info">
            <h4 class="set-name">{{ set.name }}</h4>
            <p class="set-details">
              <span class="set-code">{{ set.code }}</span>
              <span class="set-type" :class="`type-${set.type}`">{{ set.type }}</span>
            </p>
            <p v-if="set.releaseDate" class="set-date">
              📅 {{ formatDate(set.releaseDate) }}
            </p>
            <p v-if="set.block" class="set-block">
              📦 {{ set.block }}
            </p>

            <!-- Statut des cartes -->
            <div class="cards-status">
              <span v-if="set.cardsCount > 0" class="cards-count">
                🎴 {{ set.cardsCount }} cartes
              </span>
              <span v-else class="no-cards">
                ❌ Aucune carte
              </span>
              <span v-if="set.cardsSynced" class="synced-badge">✅ Synchronisé</span>
            </div>
          </div>

          <!-- Remplacement complet de la section set-actions dans votre template -->

          <div class="set-actions">
            <!-- Bouton Charger (MTG API + Scryfall fallback) -->
            <button
              @click.stop="loadSetCards(set.code)"
              :disabled="loadingCards[set.code]"
              class="load-button"
              :title="'Charger les cartes de ' + set.name"
            >
              <span v-if="loadingCards[set.code]">⏳</span>
              <span v-else>📥</span>
              {{ loadingCards[set.code] ? 'Chargement...' : 'Charger' }}
            </button>

            <!-- Bouton Scryfall spécifique -->
            <button
              @click.stop="loadFromScryfall(set.code)"
              :disabled="loadingScryfall[set.code]"
              class="scryfall-button"
              :title="'Charger depuis Scryfall pour ' + set.name"
            >
              <span v-if="loadingScryfall[set.code]">🔮</span>
              <span v-else>🔮</span>
              {{ loadingScryfall[set.code] ? 'Scryfall...' : 'Scryfall' }}
            </button>

            <!-- Bouton Sync Complète -->
            <button
              @click.stop="syncCompleteFromScryfall(set.code)"
              :disabled="syncingComplete[set.code]"
              class="complete-button"
              :title="'Synchronisation complète avec pagination pour ' + set.name"
            >
              <span v-if="syncingComplete[set.code]">🔄</span>
              <span v-else>💯</span>
              {{ syncingComplete[set.code] ? 'Sync...' : 'Complète' }}
            </button>

            <!-- Bouton spécial Final Fantasy -->
            <button
              v-if="set.code === 'FIN'"
              @click.stop="syncFinalFantasyComplete()"
              :disabled="syncingFinalFantasy"
              class="final-fantasy-button"
              :title="'Synchronisation Final Fantasy avec TOUTES les variantes (objectif: 586 cartes)'"
            >
              <span v-if="syncingFinalFantasy">🎮</span>
              <span v-else>🎯</span>
              {{ syncingFinalFantasy ? 'FF Sync...' : 'FF 586' }}
            </button>

            <!-- Bouton Sauvegarder -->
            <button
              @click.stop="saveSetData(set.code)"
              :disabled="savingCards[set.code] || set.cardsCount === 0"
              class="save-button"
              :title="'Sauvegarder en base et télécharger images de ' + set.name"
            >
              <span v-if="savingCards[set.code]">💾</span>
              <span v-else>💽</span>
              {{ savingCards[set.code] ? 'Sauvegarde...' : 'Sauvegarder' }}
            </button>

            <!-- Bouton Télécharger images -->
            <button
              @click.stop="downloadImages(set.code)"
              :disabled="downloadingImages[set.code] || set.cardsCount === 0"
              class="download-button"
              :title="'Télécharger toutes les images de ' + set.name"
            >
              <span v-if="downloadingImages[set.code]">🖼️</span>
              <span v-else>📸</span>
              {{ downloadingImages[set.code] ? 'Télécharge...' : 'Images' }}
            </button>

            <!-- Bouton Debug Pagination -->
            <button
              @click.stop="debugPagination(set.code)"
              :disabled="debuggingPagination[set.code]"
              class="debug-button"
              :title="'Debug pagination pour ' + set.name"
            >
              <span v-if="debuggingPagination[set.code]">🔍</span>
              <span v-else>🐛</span>
              {{ debuggingPagination[set.code] ? 'Debug...' : 'Debug' }}
            </button>

            <!-- NOUVEAUX BOUTONS SPÉCIFIQUES FINAL FANTASY -->
            <button
              v-if="set.code === 'FIN'"
              @click.stop="debugFinPage1()"
              :disabled="debuggingFin"
              class="debug-fin-button"
              title="Debug FIN Page 1 RAW"
            >
              <span v-if="debuggingFin">🔍</span>
              <span v-else>🎮</span>
              {{ debuggingFin ? 'FIN P1...' : 'FIN P1' }}
            </button>

            <button
              v-if="set.code === 'FIN'"
              @click.stop="debugFinPagination()"
              :disabled="debuggingFin"
              class="debug-fin-button"
              title="Debug FIN Pagination Complète"
            >
              <span v-if="debuggingFin">📄</span>
              <span v-else>📋</span>
              {{ debuggingFin ? 'FIN Pag...' : 'FIN Pag' }}
            </button>

            <!-- Bouton Diagnostic Complet FIN -->
            <button
              v-if="set.code === 'FIN'"
              @click.stop="diagnosticFinComplete()"
              :disabled="debuggingFin"
              class="diagnostic-fin-button"
              title="Diagnostic complet FIN - Pourquoi 312 au lieu de 586 cartes ?"
            >
              <span v-if="debuggingFin">🔬</span>
              <span v-else>🎯</span>
              {{ debuggingFin ? 'Diagnost...' : '312→586?' }}
            </button>
          </div>

        </div>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="pagination">
        <button
          @click="currentPage--"
          :disabled="currentPage === 1"
          class="page-button"
        >
          ← Précédent
        </button>

        <span class="page-info">
          Page {{ currentPage }} sur {{ totalPages }}
        </span>

        <button
          @click="currentPage++"
          :disabled="currentPage === totalPages"
          class="page-button"
        >
          Suivant →
        </button>
      </div>

      <!-- Statut des opérations -->
      <div v-if="operationStatus" class="operation-status" :class="operationStatus.type">
        <span>{{ operationStatus.message }}</span>
        <button @click="operationStatus = null" class="close-status">×</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMtgStore } from '@/stores/mtgStore'
import axios from 'axios'

const mtgStore = useMtgStore()

// État local
const showSelector = ref(false)
const searchTerm = ref('')
const selectedType = ref('')
const sortBy = ref('releaseDate')
const selectedSet = ref<any>(null)
const currentPage = ref(1)
const itemsPerPage = 12

// États de chargement par extension
const loadingCards = ref<Record<string, boolean>>({})
const loadingScryfall = ref<Record<string, boolean>>({})
const syncingComplete = ref<Record<string, boolean>>({})
const savingCards = ref<Record<string, boolean>>({})
const downloadingImages = ref<Record<string, boolean>>({})
const debuggingPagination = ref<Record<string, boolean>>({})
const syncingFinalFantasy = ref(false) // État spécial pour Final Fantasy
const debuggingFin = ref(false) // Debug spécial Final Fantasy

// Statut des opérations
const operationStatus = ref<{type: string, message: string} | null>(null)

// Computed
const loading = computed(() => mtgStore.loading)
const allSets = ref<any[]>([])

const availableTypes = computed(() => {
  const types = new Set(allSets.value.map(set => set.type))
  return Array.from(types).sort()
})

const filteredSets = computed(() => {
  let filtered = allSets.value

  // Filtrer par recherche
  if (searchTerm.value) {
    const term = searchTerm.value.toLowerCase()
    filtered = filtered.filter(set =>
      set.name.toLowerCase().includes(term) ||
      set.code.toLowerCase().includes(term) ||
      (set.block && set.block.toLowerCase().includes(term))
    )
  }

  // Filtrer par type
  if (selectedType.value) {
    filtered = filtered.filter(set => set.type === selectedType.value)
  }

  return filtered
})

const filteredAndSortedSets = computed(() => {
  const sorted = [...filteredSets.value]

  sorted.sort((a, b) => {
    switch (sortBy.value) {
      case 'name':
        return a.name.localeCompare(b.name)
      case 'code':
        return a.code.localeCompare(b.code)
      case 'releaseDate':
      default:
        const dateA = a.releaseDate ? new Date(a.releaseDate) : new Date(0)
        const dateB = b.releaseDate ? new Date(b.releaseDate) : new Date(0)
        return dateB.getTime() - dateA.getTime() // Plus récent en premier
    }
  })

  // Pagination
  const start = (currentPage.value - 1) * itemsPerPage
  const end = start + itemsPerPage
  return sorted.slice(start, end)
})

const totalPages = computed(() =>
  Math.ceil(filteredSets.value.length / itemsPerPage)
)

// Méthodes
const toggleSelector = async () => {
  showSelector.value = !showSelector.value

  if (showSelector.value && allSets.value.length === 0) {
    await loadAllSets()
  }
}

const loadAllSets = async () => {
  try {
    console.log('🔍 Chargement de toutes les extensions...')
    const response = await axios.get('/api/mtg/debug/all-sets')
    allSets.value = response.data.data || []
    console.log('✅ Extensions chargées:', allSets.value.length)
  } catch (error) {
    console.error('❌ Erreur chargement extensions:', error)
    showOperationStatus('error', 'Erreur lors du chargement des extensions')
  }
}

const selectSet = (set: any) => {
  selectedSet.value = set
  console.log('🎯 Extension sélectionnée:', set.code)
}

const loadSetCards = async (setCode: string) => {
  try {
    loadingCards.value[setCode] = true
    console.log('📥 Chargement des cartes pour:', setCode)

    // Essayer d'abord MTG API, puis Scryfall en fallback
    try {
      await mtgStore.fetchSetByCode(setCode)
      console.log('✅ Cartes chargées depuis MTG API')
    } catch (mtgError) {
      console.log('⚠️ MTG API échoué, essai avec Scryfall...')

      // Fallback vers Scryfall
      const response = await axios.post(`/api/scryfall/sync/${setCode}`)
      if (response.data.success) {
        console.log('✅ Synchronisation Scryfall démarrée')
        // Attendre un peu puis recharger
        setTimeout(async () => {
          await mtgStore.fetchSetByCode(setCode)
        }, 3000)
      }
    }

    // Mettre à jour le statut de l'extension dans la liste
    await refreshSetStatus(setCode)

    showOperationStatus('success', `Cartes de ${setCode} chargées avec succès`)

  } catch (error: any) {
    console.error('❌ Erreur chargement cartes:', error)
    showOperationStatus('error', `Erreur lors du chargement de ${setCode}`)
  } finally {
    loadingCards.value[setCode] = false
  }
}

const loadFromScryfall = async (setCode: string) => {
  try {
    loadingScryfall.value[setCode] = true
    console.log('🔮 Chargement depuis Scryfall pour:', setCode)

    // Synchroniser depuis Scryfall
    const response = await axios.post(`/api/scryfall/sync/${setCode}`)

    if (response.data.success) {
      showOperationStatus('success', `Synchronisation Scryfall démarrée pour ${setCode}`)

      // Attendre un peu puis recharger les données
      setTimeout(async () => {
        await refreshSetStatus(setCode)
        await mtgStore.fetchSetByCode(setCode)
        showOperationStatus('success', `Cartes Scryfall chargées pour ${setCode}`)
      }, 3000)
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur Scryfall:', error)
    showOperationStatus('error', `Erreur Scryfall pour ${setCode}`)
  } finally {
    loadingScryfall.value[setCode] = false
  }
}

const saveSetData = async (setCode: string) => {
  try {
    savingCards.value[setCode] = true
    console.log('💾 Sauvegarde en base pour:', setCode)

    // Forcer la synchronisation en base
    const response = await axios.post(`/api/mtg/admin/sync-set/${setCode}`)

    if (response.data.success) {
      await refreshSetStatus(setCode)
      showOperationStatus('success', `Extension ${setCode} sauvegardée en base`)
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur sauvegarde:', error)
    showOperationStatus('error', `Erreur lors de la sauvegarde de ${setCode}`)
  } finally {
    savingCards.value[setCode] = false
  }
}

const syncCompleteFromScryfall = async (setCode: string) => {
  try {
    syncingComplete.value[setCode] = true
    console.log('💯 Synchronisation complète depuis Scryfall pour:', setCode)

    // Synchronisation complète avec pagination
    const response = await axios.post(`/api/scryfall/sync-complete/${setCode}`)

    if (response.data.success) {
      showOperationStatus('success', `Synchronisation complète démarrée pour ${setCode}`)

      // Attendre plus longtemps pour la sync complète (pagination)
      setTimeout(async () => {
        await refreshSetStatus(setCode)
        await mtgStore.fetchSetByCode(setCode)
        showOperationStatus('success', `Toutes les cartes récupérées pour ${setCode}`)
      }, 10000) // 10 secondes pour la pagination
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur sync complète:', error)
    showOperationStatus('error', `Erreur sync complète pour ${setCode}`)
  } finally {
    syncingComplete.value[setCode] = false
  }
}

const syncFinalFantasyComplete = async () => {
  try {
    syncingFinalFantasy.value = true
    console.log('🎮 Synchronisation Final Fantasy COMPLÈTE (toutes variantes)')

    // Endpoint spécial pour Final Fantasy avec toutes les variantes
    const response = await axios.post('/api/scryfall/sync-final-fantasy-complete')

    if (response.data.success) {
      showOperationStatus('success', 'Synchronisation Final Fantasy complète démarrée (objectif: 586 cartes)')

      // Attendre plus longtemps pour la synchronisation complète avec variantes
      setTimeout(async () => {
        await refreshSetStatus('FIN')
        await mtgStore.fetchSetByCode('FIN')
        showOperationStatus('success', 'Final Fantasy synchronisé avec toutes les variantes!')
      }, 15000) // 15 secondes pour laisser le temps à toutes les variantes
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur sync Final Fantasy complète:', error)
    showOperationStatus('error', 'Erreur lors de la synchronisation Final Fantasy complète')
  } finally {
    syncingFinalFantasy.value = false
  }
}

const downloadImages = async (setCode: string) => {
  try {
    downloadingImages.value[setCode] = true
    console.log('📸 Téléchargement des images pour:', setCode)

    // Déclencher le téléchargement des images
    const response = await axios.post(`/api/images/download-set/${setCode}`)

    if (response.status === 202) { // Accepted
      showOperationStatus('success', `Téléchargement des images de ${setCode} démarré`)

      // Optionnel : surveiller le progrès
      setTimeout(() => checkDownloadProgress(setCode), 5000)
    }

  } catch (error: any) {
    console.error('❌ Erreur téléchargement images:', error)
    showOperationStatus('error', `Erreur lors du téléchargement des images de ${setCode}`)
  } finally {
    downloadingImages.value[setCode] = false
  }
}

const debugPagination = async (setCode: string) => {
  try {
    debuggingPagination.value[setCode] = true
    console.log('🔍 Debug pagination pour:', setCode)

    // Test debug direct
    const response = await axios.get(`/api/scryfall/debug-pagination-problem/${setCode}`)

    console.log('🐛 Résultat debug:', response.data)

    if (response.data.success) {
      const data = response.data.data
      const cardsReceived = data.cardsReceived || 0
      const isPaginationWorking = data.isPaginationWorking || false

      let message = `Debug ${setCode}: ${cardsReceived} cartes récupérées`
      let type = 'success'

      if (!isPaginationWorking && cardsReceived <= 175) {
        message = `PROBLÈME: Seulement ${cardsReceived} cartes pour ${setCode} - Pagination échouée!`
        type = 'error'
      }

      showOperationStatus(type, message)

      // Afficher plus de détails dans la console
      console.log('📊 Détails debug:', {
        setCode,
        cardsReceived,
        isPaginationWorking,
        problem: data.problem,
        sampleCards: data.sampleCards
      })
    }

  } catch (error: any) {
    console.error('❌ Erreur debug pagination:', error)
    showOperationStatus('error', `Erreur debug pagination ${setCode}`)
  } finally {
    debuggingPagination.value[setCode] = false
  }
}

const debugFinPage1 = async () => {
  try {
    debuggingFin.value = true
    console.log('🔍 Debug Final Fantasy page 1 RAW')

    const response = await axios.get('/api/scryfall/debug-fin-raw-page1')
    console.log('🐛 Debug FIN Page 1:', response.data)

    if (response.data.success) {
      const data = response.data.data
      const totalCards = data.totalCards || 0
      const page1Cards = data.dataArraySize || 0
      const hasMore = data.hasMoreValue

      const message = `FIN Page 1: ${page1Cards} cartes, total=${totalCards}, hasMore=${hasMore}`
      showOperationStatus('success', message)

      console.log('📊 Analyse FIN Page 1:', {
        totalCards,
        page1Cards,
        hasMore,
        analysis: data.analysis
      })
    }

  } catch (error: any) {
    console.error('❌ Erreur debug FIN page 1:', error)
    showOperationStatus('error', 'Erreur debug FIN page 1')
  } finally {
    debuggingFin.value = false
  }
}

const debugFinPagination = async () => {
  try {
    debuggingFin.value = true
    console.log('📄 Debug Final Fantasy pagination complète')

    const response = await axios.get('/api/scryfall/debug-fin-manual-pagination')
    console.log('🐛 Debug FIN Pagination:', response.data)

    if (response.data.success) {
      const data = response.data.data
      const totalFound = data.totalCardsFound || 0
      const pagesSuccessful = data.pagesSuccessful || 0

      const message = `FIN Pagination: ${totalFound} cartes sur ${pagesSuccessful} pages`
      const type = totalFound >= 300 ? 'success' : 'error'
      showOperationStatus(type, message)

      console.log('📊 Analyse FIN Pagination:', {
        totalFound,
        pagesSuccessful,
        analysis: data.analysis,
        pages: data.pages
      })
    }

  } catch (error: any) {
    console.error('❌ Erreur debug FIN pagination:', error)
    showOperationStatus('error', 'Erreur debug FIN pagination')
  } finally {
    debuggingFin.value = false
  }
}

const checkDownloadProgress = async (setCode: string) => {
  try {
    const response = await axios.get('/api/images/stats')
    console.log('📊 Statistiques téléchargement:', response.data)
  } catch (error) {
    console.error('❌ Erreur vérification progrès:', error)
  }
}

const refreshSetStatus = async (setCode: string) => {
  try {
    const response = await axios.get(`/api/mtg/sets/${setCode}/with-cards`)
    if (response.data.success) {
      // Mettre à jour l'extension dans la liste
      const setIndex = allSets.value.findIndex(s => s.code === setCode)
      if (setIndex !== -1) {
        allSets.value[setIndex] = {
          ...allSets.value[setIndex],
          cardsCount: response.data.data.totalCards,
          cardsSynced: response.data.data.cardsSynced
        }
      }
    }
  } catch (error) {
    console.error('❌ Erreur refresh statut:', error)
  }
}

const showOperationStatus = (type: string, message: string) => {
  operationStatus.value = { type, message }
  // Auto-fermer après 5 secondes
  setTimeout(() => {
    operationStatus.value = null
  }, 5000)
}

const formatDate = (dateString: string): string => {
  try {
    return new Date(dateString).toLocaleDateString('fr-FR')
  } catch {
    return dateString
  }
}


// Ajoutez cette fonction dans la section des méthodes de SetSelector.vue

const diagnosticFinComplete = async () => {
  try {
    debuggingFin.value = true
    console.log('🔬 Diagnostic complet Final Fantasy - 312 vs 586 cartes')

    const response = await axios.get('/api/scryfall/diagnostic-fin-complete')
    console.log('🎯 Diagnostic FIN complet:', response.data)

    if (response.data.success) {
      const data = response.data.data
      const maxCardsFound = data.maxCardsFound || 0
      const bestQuery = data.bestQuery || 'Aucune'
      const analysis = data.analysis || {}

      let message = `Diagnostic FIN: ${maxCardsFound} cartes max trouvées`
      let type = 'success'

      if (maxCardsFound >= 586) {
        message = `🎉 SOLUTION TROUVÉE! ${maxCardsFound} cartes disponibles avec la bonne requête!`
        type = 'success'
      } else if (maxCardsFound > 312) {
        message = `📈 AMÉLIORATION: ${maxCardsFound} cartes vs 312 actuelles - Requête optimisée disponible!`
        type = 'success'
      } else {
        message = `❌ LIMITATION: Maximum ${maxCardsFound} cartes disponibles sur Scryfall`
        type = 'error'
      }

      showOperationStatus(type, message)

      // Afficher les détails dans la console pour debug
      console.log('📊 Analyse détaillée FIN:', {
        objectif: 586,
        maxTrouve: maxCardsFound,
        meilleureRequete: bestQuery,
        analyse: analysis,
        recommandations: data.recommendations
      })

      // Si on a trouvé une meilleure solution, proposer de l'utiliser
      if (maxCardsFound > 312) {
        console.log('🚀 SOLUTION AMÉLIORÉE DISPONIBLE!')
        console.log('📋 Recommandations:', data.recommendations)
        console.log('🔗 Meilleure requête:', bestQuery)

        // Optionnel: déclencher automatiquement la sync avec la meilleure méthode
        if (maxCardsFound >= 500) {
          setTimeout(() => {
            console.log('🎯 Déclenchement automatique de la sync optimisée...')
            syncFinalFantasyComplete()
          }, 3000)
        }
      }

    } else {
      showOperationStatus('error', 'Erreur lors du diagnostic FIN')
    }

  } catch (error: any) {
    console.error('❌ Erreur diagnostic FIN complet:', error)
    showOperationStatus('error', 'Erreur diagnostic FIN complet')
  } finally {
    debuggingFin.value = false
  }
}



// Lifecycle
onMounted(() => {
  console.log('🎛️ SetSelector monté avec fonctionnalités avancées')
})
</script>

<style scoped>
.set-selector {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  backdrop-filter: blur(10px);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.selector-header h3 {
  color: #ffd700;
  margin: 0;
}

.toggle-button {
  padding: 0.5rem 1rem;
  background: #3498db;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.toggle-button:hover {
  background: #2980b9;
  transform: translateY(-1px);
}

.selector-content {
  animation: fadeIn 0.3s ease-out;
}

.search-sets {
  margin-bottom: 1rem;
}

.search-input {
  width: 100%;
  padding: 0.75rem;
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  font-size: 1rem;
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.7);
}

.search-input:focus {
  outline: none;
  border-color: #ffd700;
  box-shadow: 0 0 10px rgba(255, 215, 0, 0.3);
}

.filters {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}

.filter-select {
  padding: 0.5rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  min-width: 150px;
}

.filter-select option {
  background: #2c3e50;
  color: white;
}

.sets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.set-item {
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid transparent;
  border-radius: 8px;
  padding: 1rem;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.set-item:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 215, 0, 0.5);
  transform: translateY(-2px);
}

.set-item.selected {
  border-color: #ffd700;
  background: rgba(255, 215, 0, 0.1);
}

.set-item.has-cards {
  border-left: 4px solid #27ae60;
}

.set-item.synced {
  border-right: 4px solid #3498db;
}

.set-info {
  flex: 1;
}

.set-name {
  margin: 0 0 0.5rem 0;
  color: #ffd700;
  font-size: 1.1rem;
  line-height: 1.3;
}

.set-details {
  display: flex;
  gap: 0.5rem;
  margin: 0.25rem 0;
  align-items: center;
  flex-wrap: wrap;
}

.set-code {
  background: rgba(255, 255, 255, 0.2);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  font-size: 0.9rem;
}

.set-type {
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
}

.type-expansion { background: #e74c3c; color: white; }
.type-core { background: #3498db; color: white; }
.type-draft_innovation { background: #9b59b6; color: white; }
.type-commander { background: #f39c12; color: white; }
.type-masters { background: #2ecc71; color: white; }

.set-date,
.set-block {
  margin: 0.25rem 0;
  font-size: 0.9rem;
  opacity: 0.9;
}

.cards-status {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin-top: 0.5rem;
  flex-wrap: wrap;
}

.cards-count {
  background: rgba(39, 174, 96, 0.2);
  color: #27ae60;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
}

.no-cards {
  background: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
}

.synced-badge {
  background: rgba(52, 152, 219, 0.2);
  color: #3498db;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
}

.set-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.load-button,
.scryfall-button,
.complete-button,
.save-button,
.download-button,
.debug-button,
.final-fantasy-button {
  flex: 1;
  min-width: 70px;
  padding: 0.5rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.75rem;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.load-button {
  background: linear-gradient(45deg, #3498db, #2980b9);
  color: white;
}

.scryfall-button {
  background: linear-gradient(45deg, #9b59b6, #8e44ad);
  color: white;
}

.complete-button {
  background: linear-gradient(45deg, #e67e22, #d35400);
  color: white;
}

.save-button {
  background: linear-gradient(45deg, #27ae60, #229954);
  color: white;
}

.download-button {
  background: linear-gradient(45deg, #f39c12, #e67e22);
  color: white;
}

.debug-button {
  background: linear-gradient(45deg, #9b59b6, #8e44ad);
  color: white;
  font-size: 0.7rem;
}

.debug-button:hover:not(:disabled) {
  background: linear-gradient(45deg, #8e44ad, #7d3c98);
}

.final-fantasy-button {
  background: linear-gradient(45deg, #ff6b9d, #c44569);
  color: white;
  border: 2px solid #ff3838;
  font-weight: 700;
  text-shadow: 1px 1px 2px rgba(0,0,0,0.3);
}

.load-button:hover:not(:disabled),
.scryfall-button:hover:not(:disabled),
.complete-button:hover:not(:disabled),
.save-button:hover:not(:disabled),
.download-button:hover:not(:disabled),
.final-fantasy-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.load-button:disabled,
.scryfall-button:disabled,
.complete-button:disabled,
.save-button:disabled,
.download-button:disabled,
.debug-button:disabled,
.final-fantasy-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.operation-status {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  animation: slideIn 0.3s ease-out;
}

.operation-status.success {
  background: rgba(39, 174, 96, 0.2);
  border: 1px solid #27ae60;
  color: #27ae60;
}

.operation-status.error {
  background: rgba(231, 76, 60, 0.2);
  border: 1px solid #e74c3c;
  color: #e74c3c;
}

.close-status {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.2rem;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}

.page-button {
  padding: 0.5rem 1rem;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.page-button:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
}

.page-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-weight: 600;
  color: #ffd700;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (max-width: 768px) {
  .sets-grid {
    grid-template-columns: 1fr;
  }

  .set-actions {
    flex-direction: column;
  }

  .load-button,
  .scryfall-button,
  .save-button,
  .download-button,
  .debug-button,
  .final-fantasy-button {
    min-width: auto;
  }

  .filters {
    flex-direction: column;
  }

  .filter-select {
    min-width: auto;
  }
}
/* Nouveau style pour les boutons debug FIN */
.debug-fin-button {
  flex: 1;
  min-width: 70px;
  padding: 0.5rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.7rem;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  background: linear-gradient(45deg, #ff6b9d, #c44569);
  color: white;
  border: 1px solid #ff3838;
}

.debug-fin-button:hover:not(:disabled) {
  background: linear-gradient(45deg, #c44569, #8e2638);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 56, 56, 0.3);
}

.debug-fin-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.diagnostic-fin-button {
  flex: 1;
  min-width: 80px;
  padding: 0.5rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 700;
  font-size: 0.7rem;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  background: linear-gradient(45deg, #ff4757, #ff3742);
  color: white;
  border: 2px solid #ff6b9d;
  text-shadow: 1px 1px 2px rgba(0,0,0,0.3);
  box-shadow: 0 2px 8px rgba(255, 71, 87, 0.3);
}

.diagnostic-fin-button:hover:not(:disabled) {
  background: linear-gradient(45deg, #ff3742, #ff2f3a);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 71, 87, 0.4);
  border-color: #ff4757;
}

.diagnostic-fin-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
