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

          <!-- Actions pour chaque extension -->
          <div class="set-actions">
            <!-- Boutons standards pour toutes les extensions -->
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

            <button
              @click.stop="syncFromScryfall(set.code)"
              :disabled="loadingScryfall[set.code]"
              class="scryfall-button"
              title="Synchroniser depuis Scryfall"
            >
              <span v-if="loadingScryfall[set.code]">🔄</span>
              <span v-else>🔄</span>
              {{ loadingScryfall[set.code] ? 'Sync...' : 'Sync' }}
            </button>

            <button
              @click.stop="downloadImages(set.code)"
              :disabled="downloadingImages[set.code]"
              class="download-button"
              title="Télécharger les images"
            >
              <span v-if="downloadingImages[set.code]">⬇️</span>
              <span v-else>📥</span>
              {{ downloadingImages[set.code] ? 'DL...' : 'Images' }}
            </button>

            <!-- BOUTONS SPÉCIAUX POUR FINAL FANTASY (FIN) -->
            <template v-if="set.code === 'FIN'">
              <!-- Bouton Synchronisation Avancée FIN -->
              <button
                @click.stop="syncFinalFantasyAdvanced()"
                :disabled="syncingAdvancedFin"
                class="sync-advanced-fin-button"
                title="Synchronisation avancée FIN - Toutes les variantes pour atteindre 586 cartes"
              >
                <span v-if="syncingAdvancedFin">🔄</span>
                <span v-else>🎯</span>
                {{ syncingAdvancedFin ? 'Sync 586...' : 'Sync 586!' }}
              </button>

              <!-- Bouton Diagnostic FIN -->
              <button
                @click.stop="diagnosticFinalFantasyComplet()"
                :disabled="debuggingFin"
                class="diagnostic-fin-button"
                title="Diagnostic complet FIN - Analyser pourquoi 312 au lieu de 586"
              >
                <span v-if="debuggingFin">🔬</span>
                <span v-else>🩺</span>
                {{ debuggingFin ? 'Diagnostic...' : 'Diagnostic' }}
              </button>

              <!-- Affichage spécial du statut FIN -->
              <div class="fin-status" v-if="set.cardsCount">

                <!-- NOUVEAU affichage réaliste -->
                <div class="fin-status" v-if="set.code === 'FIN' && set.cardsCount">
  <span class="cards-count" :class="{ 'complete': set.cardsCount >= 312 }">
    {{ set.cardsCount }}/312
  </span>
                  <span v-if="set.cardsCount >= 312" class="success-badge">✅ COMPLET</span>
                  <span v-else-if="set.cardsCount >= 250" class="nearly-badge">📊 Presque</span>
                  <span v-else class="warning-badge">⚠️ Partiel</span>
                  <small class="set-note">Set FIN officiel</small>
                </div>



                <span v-else class="warning-badge">⚠️</span>
              </div>
            </template>

            <!-- Bouton Debug pagination -->
            <button
              @click.stop="debugPagination(set.code)"
              :disabled="debuggingPagination[set.code]"
              class="debug-button"
              title="Debug pagination"
            >
              <span v-if="debuggingPagination[set.code]">🔍</span>
              <span v-else>🔍</span>
              {{ debuggingPagination[set.code] ? 'Debug...' : 'Debug' }}
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

// NOUVELLES VARIABLES pour FIN
const syncingAdvancedFin = ref(false)
const debuggingFin = ref(false)

// Statut des opérations
const operationStatus = ref<{type: string, message: string} | null>(null)

// Computed
const loading = computed(() => mtgStore.loading)
const allSets = ref<any[]>([])

// Charger les extensions au montage
onMounted(async () => {
  await loadAllSets()
})

// Méthodes utilitaires
const toggleSelector = () => {
  showSelector.value = !showSelector.value
}

const selectSet = (set: any) => {
  selectedSet.value = set
  mtgStore.fetchSetByCode(set.code)
}

const formatDate = (dateString: string): string => {
  try {
    return new Date(dateString).toLocaleDateString('fr-FR')
  } catch {
    return dateString
  }
}

// MÉTHODES PRINCIPALES

/**
 * Charger toutes les extensions
 */
const loadAllSets = async () => {
  try {
    const response = await axios.get('/api/mtg/sets')
    if (response.data.success) {
      allSets.value = response.data.data || []
      console.log('📦 Extensions chargées:', allSets.value.length)
    }
  } catch (error) {
    console.error('❌ Erreur chargement extensions:', error)
    showOperationStatus('error', 'Erreur lors du chargement des extensions')
  }
}

/**
 * Charger les cartes d'une extension (MTG API + Scryfall fallback)
 */
const loadSetCards = async (setCode: string) => {
  try {
    loadingCards.value[setCode] = true
    console.log('📥 Chargement cartes pour:', setCode)

    const response = await axios.get(`/api/mtg/sets/${setCode}/with-cards`)

    if (response.data.success) {
      const data = response.data.data
      showOperationStatus('success', `${data.totalCards} cartes chargées pour ${setCode}`)

      // Mettre à jour le store
      await mtgStore.fetchSetByCode(setCode)

      // Rafraîchir la liste des extensions
      await refreshSetStatus(setCode)
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur chargement cartes:', error)
    showOperationStatus('error', `Erreur chargement ${setCode}`)
  } finally {
    loadingCards.value[setCode] = false
  }
}

/**
 * Synchroniser depuis Scryfall
 */
const syncFromScryfall = async (setCode: string) => {
  try {
    loadingScryfall.value[setCode] = true
    console.log('🔄 Synchronisation Scryfall pour:', setCode)

    const response = await axios.post(`/api/scryfall/sync/${setCode}`)

    if (response.data.success) {
      showOperationStatus('success', `Synchronisation Scryfall démarrée pour ${setCode}`)

      // Attendre un peu puis rafraîchir
      setTimeout(async () => {
        await refreshSetStatus(setCode)
        await mtgStore.fetchSetByCode(setCode)
      }, 3000)
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur sync Scryfall:', error)
    showOperationStatus('error', `Erreur sync Scryfall pour ${setCode}`)
  } finally {
    loadingScryfall.value[setCode] = false
  }
}

/**
 * Télécharger les images
 */
const downloadImages = async (setCode: string) => {
  try {
    downloadingImages.value[setCode] = true
    console.log('📥 Téléchargement images pour:', setCode)

    const response = await axios.post(`/api/images/download/${setCode}`)

    if (response.data.success) {
      showOperationStatus('success', `Téléchargement images démarré pour ${setCode}`)
    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur téléchargement images:', error)
    showOperationStatus('error', `Erreur téléchargement images ${setCode}`)
  } finally {
    downloadingImages.value[setCode] = false
  }
}

/**
 * Debug pagination
 */
const debugPagination = async (setCode: string) => {
  try {
    debuggingPagination.value[setCode] = true
    console.log('🔍 Debug pagination pour:', setCode)

    const response = await axios.get(`/api/scryfall/debug-pagination/${setCode}`)

    if (response.data.success) {
      console.log('🔍 Résultats debug:', response.data.data)
      showOperationStatus('info', `Debug pagination terminé pour ${setCode}`)
    }

  } catch (error: any) {
    console.error('❌ Erreur debug pagination:', error)
    showOperationStatus('error', `Erreur debug pagination ${setCode}`)
  } finally {
    debuggingPagination.value[setCode] = false
  }
}

// Et dans les méthodes JavaScript :

const syncFinalFantasyRealistic = async () => {
  try {
    syncingAdvancedFin.value = true
    console.log('🎮 Synchronisation Final Fantasy - Objectif réaliste: 312 cartes')

    // Utiliser l'endpoint de sync normal qui fonctionne déjà !
    const response = await axios.post('/api/scryfall/sync/FIN')

    if (response.data.success) {
      showOperationStatus('success', 'Synchronisation Final Fantasy démarrée')

      setTimeout(async () => {
        await refreshSetStatus('FIN')
        const updatedSet = allSets.value.find(s => s.code === 'FIN')

        if (updatedSet && updatedSet.cardsCount >= 312) {
          showOperationStatus('success', `🎉 Final Fantasy COMPLET: ${updatedSet.cardsCount}/312 cartes`)
        } else if (updatedSet && updatedSet.cardsCount >= 250) {
          showOperationStatus('warning', `📊 Final Fantasy quasi-complet: ${updatedSet.cardsCount}/312 cartes`)
        } else {
          showOperationStatus('info', `📥 Final Fantasy: ${updatedSet?.cardsCount || 0}/312 cartes récupérées`)
        }
      }, 3000)
    }

  } catch (error: any) {
    console.error('❌ Erreur sync Final Fantasy:', error)
    showOperationStatus('error', 'Erreur synchronisation Final Fantasy')
  } finally {
    syncingAdvancedFin.value = false
  }
}

// Diagnostic mis à jour
const diagnosticFinalFantasyRealistic = async () => {
  try {
    debuggingFin.value = true
    console.log('🔬 Diagnostic Final Fantasy - Objectif réaliste: 312 cartes')

    const response = await axios.get('/api/scryfall/debug-312-cards')

    if (response.data.success) {
      const data = response.data.data

      console.log('📊 DIAGNOSTIC FINAL FANTASY:')
      console.log('• Cartes en base:', data.cartesEnBase)
      console.log('• Objectif réaliste: 312 cartes')
      console.log('• Statut:', data.cartesEnBase >= 312 ? '✅ COMPLET' : '⚠️ Partiel')

      if (data.conclusion) {
        console.log('• Explication:', data.conclusion.explication)
        console.log('• Statut tech:', data.conclusion.statut)
      }

      const status = data.cartesEnBase >= 312 ? 'success' : 'info'
      const message = `Diagnostic: ${data.cartesEnBase}/312 cartes Final Fantasy`
      showOperationStatus(status, message)

    }

  } catch (error: any) {
    console.error('❌ Erreur diagnostic Final Fantasy:', error)
    showOperationStatus('error', 'Erreur diagnostic Final Fantasy')
  } finally {
    debuggingFin.value = false
  }
}




/**
 * NOUVELLE MÉTHODE - Synchronisation avancée Final Fantasy
 */
const syncFinalFantasyAdvanced = async () => {
  try {
    syncingAdvancedFin.value = true
    console.log('🎯 Synchronisation AVANCÉE Final Fantasy - Objectif 586 cartes')

    const response = await axios.post('/api/scryfall/sync-final-fantasy-advanced')
    console.log('🎮 Réponse sync FIN avancée:', response.data)

    if (response.data.success) {
      const data = response.data.data
      const cartesSauvegardées = data.cartesSauvegardées || 0
      const objectifAtteint = data.objectifAtteint || false
      const meilleureRequête = data.meilleureRequête || 'Inconnue'

      let message = `FIN Avancé: ${cartesSauvegardées} cartes récupérées`
      if (objectifAtteint) {
        message = `🎉 OBJECTIF ATTEINT! ${cartesSauvegardées} cartes FIN`
      } else {
        message = `⚠️ ${cartesSauvegardées} cartes FIN (objectif: 586)`
      }

      showOperationStatus(objectifAtteint ? 'success' : 'warning', message)

      console.log('📊 Détails sync FIN avancée:', {
        cartesSauvegardées,
        objectifAtteint,
        meilleureRequête,
        répartition: data.répartitionRareté
      })

      // Rafraîchir les données
      setTimeout(async () => {
        await refreshSetStatus('FIN')
        await mtgStore.fetchSetByCode('FIN')
      }, 2000)

    } else {
      throw new Error(response.data.message)
    }

  } catch (error: any) {
    console.error('❌ Erreur sync FIN avancée:', error)
    showOperationStatus('error', 'Erreur synchronisation FIN avancée')
  } finally {
    syncingAdvancedFin.value = false
  }
}

/**
 * NOUVELLE MÉTHODE - Diagnostic Final Fantasy complet
 */
const diagnosticFinalFantasyComplet = async () => {
  try {
    debuggingFin.value = true
    console.log('🔬 Diagnostic Final Fantasy COMPLET')

    const response = await axios.get('/api/scryfall/diagnostic-fin-complete')

    if (response.data.success) {
      const data = response.data.data

      console.log('📊 DIAGNOSTIC FIN COMPLET:', {
        problème: data.problemeActuel,
        objectif: data.objectif,
        officielCount: data.setEndpointInfo?.officialCardCount,
        maxTrouvé: data.maxCardsFound,
        meilleureRequête: data.bestQuery,
        analyse: data.analysis
      })

      // Utiliser showOperationStatus au lieu d'alert pour une meilleure UX
      showOperationStatus('info', 'Diagnostic terminé - Voir console pour détails')

      // Afficher dans la console de manière organisée
      console.table({
        'Objectif': data.objectif,
        'Actuel': data.problemeActuel,
        'Max trouvé': data.maxCardsFound,
        'Meilleure requête': data.bestQuery
      })

    }

  } catch (error: any) {
    console.error('❌ Erreur diagnostic FIN:', error)
    showOperationStatus('error', 'Erreur diagnostic FIN')
  } finally {
    debuggingFin.value = false
  }
}

/**
 * Rafraîchir le statut d'une extension
 */
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

/**
 * Afficher un statut d'opération
 */
const showOperationStatus = (type: string, message: string) => {
  operationStatus.value = { type, message }
  setTimeout(() => {
    operationStatus.value = null
  }, 5000)
}

// COMPUTED PROPERTIES

const availableTypes = computed(() => {
  const types = new Set(allSets.value.map(set => set.type).filter(Boolean))
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
  let sorted = [...filteredSets.value]

  // Trier
  switch (sortBy.value) {
    case 'name':
      sorted.sort((a, b) => a.name.localeCompare(b.name))
      break
    case 'code':
      sorted.sort((a, b) => a.code.localeCompare(b.code))
      break
    case 'releaseDate':
    default:
      sorted.sort((a, b) => {
        const dateA = a.releaseDate ? new Date(a.releaseDate) : new Date(0)
        const dateB = b.releaseDate ? new Date(b.releaseDate) : new Date(0)
        return dateB.getTime() - dateA.getTime() // Plus récent en premier
      })
      break
  }

  return sorted
})

const paginatedSets = computed(() => {
  const start = (currentPage.value - 1) * itemsPerPage
  const end = start + itemsPerPage
  return filteredAndSortedSets.value.slice(start, end)
})

const totalPages = computed(() => {
  return Math.ceil(filteredAndSortedSets.value.length / itemsPerPage)
})
</script>



<style scoped>
.set-selector {
  background: rgba(0, 0, 0, 0.8);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.selector-header h3 {
  margin: 0;
  color: #ffd700;
  font-size: 1.4rem;
}

.toggle-button {
  background: linear-gradient(45deg, #3498db, #2980b9);
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
}

.toggle-button:hover {
  background: linear-gradient(45deg, #2980b9, #1abc9c);
  transform: translateY(-2px);
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
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  font-size: 1rem;
  backdrop-filter: blur(5px);
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.6);
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
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
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
  opacity: 0.8;
}

.cards-status {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
  margin: 0.5rem 0;
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
  align-items: center;
}

.load-button,
.scryfall-button,
.complete-button,
.save-button,
.download-button,
.debug-button {
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
}

.load-button {
  background: linear-gradient(45deg, #3498db, #2980b9);
  color: white;
}

.scryfall-button {
  background: linear-gradient(45deg, #9b59b6, #8e44ad);
  color: white;
}

.download-button {
  background: linear-gradient(45deg, #e67e22, #d35400);
  color: white;
}

.debug-button {
  background: linear-gradient(45deg, #95a5a6, #7f8c8d);
  color: white;
}

/* NOUVEAUX STYLES pour les boutons FIN */
.sync-advanced-fin-button {
  background: linear-gradient(45deg, #ff6b6b, #4ecdc4);
  color: white;
  border: none;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-weight: bold;
  font-size: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  margin: 2px;
  flex: 1;
  min-width: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.sync-advanced-fin-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.sync-advanced-fin-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.diagnostic-fin-button {
  background: linear-gradient(45deg, #9b59b6, #3498db);
  color: white;
  border: none;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-weight: bold;
  font-size: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  margin: 2px;
  flex: 1;
  min-width: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.diagnostic-fin-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.diagnostic-fin-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.fin-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 0.8rem;
}

.fin-status .cards-count.incomplete {
  background: rgba(243, 156, 18, 0.2);
  color: #f39c12;
}

.fin-status .cards-count.complete {
  background: rgba(39, 174, 96, 0.2);
  color: #27ae60;
  font-weight: bold;
}

.success-badge {
  color: #27ae60;
  font-size: 1rem;
}

.warning-badge {
  color: #f39c12;
  font-size: 1rem;
}

.nearly-badge {
  color: #f39c12;
  font-size: 1rem;
}

.set-note {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.7rem;
  font-style: italic;
}

.load-button:hover:not(:disabled),
.scryfall-button:hover:not(:disabled),
.complete-button:hover:not(:disabled),
.save-button:hover:not(:disabled),
.download-button:hover:not(:disabled),
.debug-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.load-button:disabled,
.scryfall-button:disabled,
.complete-button:disabled,
.save-button:disabled,
.download-button:disabled,
.debug-button:disabled {
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

.operation-status.warning {
  background: rgba(243, 156, 18, 0.2);
  border: 1px solid #f39c12;
  color: #f39c12;
}

.operation-status.info {
  background: rgba(52, 152, 219, 0.2);
  border: 1px solid #3498db;
  color: #3498db;
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
  .sync-advanced-fin-button,
  .diagnostic-fin-button {
    min-width: auto;
  }

  .filters {
    flex-direction: column;
  }

  .filter-select {
    min-width: auto;
  }
}
</style>
