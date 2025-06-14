<template>
  <div class="set-selector">
    <!-- Header avec contrôle de chargement -->
    <div class="selector-header">
      <h3>🎴 Choisir une extension</h3>
      <div class="global-controls">
        <button @click="loadSetsFromAPI" :disabled="loadingGlobal" class="api-load-button">
          {{ loadingGlobal ? 'Chargement...' : '📥 Charger depuis API' }}
        </button>
        <button @click="toggleSelector" class="toggle-button">
          {{ showSelector ? 'Masquer' : 'Afficher' }} les extensions
        </button>
      </div>
    </div>

    <div v-if="showSelector" class="selector-content">
      <!-- Recherche d'extensions -->
      <div class="search-sets">
        <input v-model="searchTerm" placeholder="Rechercher une extension..." class="search-input" />
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
        <div v-for="set in paginatedSets" :key="set.code" class="set-item">
          <!-- Informations de l'extension -->
          <div class="set-info">
            <h4 class="set-name">{{ set.name }}</h4>
            <p class="set-details">
              <span class="set-code">{{ set.code }}</span>
              <span class="set-type" :class="`type-${set.type}`">{{ set.type }}</span>
            </p>
            <p v-if="set.releaseDate" class="set-date">
              📅 {{ formatDate(set.releaseDate) }}
            </p>
            <div class="cards-status">
              <span v-if="set.cardsCount > 0" class="cards-count">
                🎴 {{ set.cardsCount }} cartes
              </span>
              <span v-else class="no-cards">❌ Aucune carte</span>
              <span v-if="set.cardsSynced" class="synced-badge">✅ Synchronisé</span>
            </div>
          </div>

          <!-- CONTRÔLES MANUELS PAR EXTENSION -->
          <div class="manual-controls">
            <!-- 1. Chargement sans sauvegarde -->
            <div class="load-section">
              <button @click="loadCardsFromAPI(set.code)" :disabled="loadingCards[set.code]" class="load-api-button">
                {{ loadingCards[set.code] ? '⏳' : '📥' }} API MTG
              </button>
              <button @click="loadCardsFromScryfall(set.code)" :disabled="loadingScryfall[set.code]" class="load-scryfall-button">
                {{ loadingScryfall[set.code] ? '🔄' : '🔮' }} Scryfall
              </button>
            </div>

            <!-- 2. Sauvegarde individuelle -->
            <div class="save-section">
              <button @click="saveExtensionToDatabase(set.code)" :disabled="savingSets[set.code]" class="save-set-button">
                {{ savingSets[set.code] ? '💾...' : '💾' }} Sauv. Extension
              </button>
              <button @click="saveCardsToDatabase(set.code)" :disabled="savingCards[set.code]" class="save-cards-button">
                {{ savingCards[set.code] ? '🎴...' : '🎴' }} Sauv. Cartes
              </button>
            </div>

            <!-- 3. Actions supplémentaires -->
            <div class="action-section">
              <button @click="downloadExtensionImages(set.code)" :disabled="downloadingImages[set.code]" class="download-button">
                {{ downloadingImages[set.code] ? '⬇️...' : '📸' }} Images
              </button>
              <button @click="viewSetDetails(set.code)" class="view-button">
                👁️ Voir détails
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="pagination">
        <button @click="currentPage--" :disabled="currentPage === 1" class="page-button">
          ← Précédent
        </button>
        <span class="page-info">Page {{ currentPage }} sur {{ totalPages }}</span>
        <button @click="currentPage++" :disabled="currentPage === totalPages" class="page-button">
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

// États de chargement
const loadingGlobal = ref(false)
const loadingCards = ref<Record<string, boolean>>({})
const loadingScryfall = ref<Record<string, boolean>>({})
const savingCards = ref<Record<string, boolean>>({})
const savingSets = ref<Record<string, boolean>>({})
const downloadingImages = ref<Record<string, boolean>>({})

// Statut des opérations
const operationStatus = ref<{type: string, message: string} | null>(null)

// Data
const allSets = ref<any[]>([])

// Charger les extensions au montage
onMounted(async () => {
  await loadAllSets()
})

// ========== MÉTHODES UTILITAIRES ==========

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

const showOperationStatus = (type: string, message: string) => {
  operationStatus.value = { type, message }
  setTimeout(() => {
    operationStatus.value = null
  }, 5000)
}

// ========== MÉTHODES DE CHARGEMENT ==========

/**
 * Charger toutes les extensions existantes
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
 * Charger toutes les extensions depuis l'API (sans sauvegarder)
 */
const loadSetsFromAPI = async () => {
  try {
    loadingGlobal.value = true
    const response = await axios.get('/api/mtg/sets/load-from-api')
    if (response.data.success) {
      allSets.value = response.data.data
      showOperationStatus('success', `${response.data.data.length} extensions chargées depuis l'API (non sauvegardées)`)
    }
  } catch (error) {
    showOperationStatus('error', 'Erreur chargement depuis API')
  } finally {
    loadingGlobal.value = false
  }
}

/**
 * Charger les cartes depuis l'API MTG (sans sauvegarder)
 */
const loadCardsFromAPI = async (setCode: string) => {
  try {
    loadingCards.value[setCode] = true
    const response = await axios.get(`/api/mtg/sets/${setCode}/cards`)
    if (response.data.success) {
      showOperationStatus('info', `${response.data.data.length} cartes chargées pour ${setCode} (non sauvegardées)`)
    }
  } catch (error) {
    showOperationStatus('error', `Erreur chargement cartes ${setCode}`)
  } finally {
    loadingCards.value[setCode] = false
  }
}

/**
 * Charger les cartes depuis Scryfall (sans sauvegarder)
 */
const loadCardsFromScryfall = async (setCode: string) => {
  try {
    loadingScryfall.value[setCode] = true
    const response = await axios.get(`/api/mtg/sets/${setCode}/load-from-scryfall`)
    if (response.data.success) {
      showOperationStatus('info', `${response.data.data.length} cartes Scryfall chargées pour ${setCode} (non sauvegardées)`)
    }
  } catch (error) {
    showOperationStatus('error', `Erreur chargement Scryfall ${setCode}`)
  } finally {
    loadingScryfall.value[setCode] = false
  }
}

// ========== MÉTHODES DE SAUVEGARDE ==========

/**
 * Sauvegarder UNE extension spécifique en base
 */
const saveExtensionToDatabase = async (setCode: string) => {
  try {
    savingSets.value[setCode] = true
    const response = await axios.post(`/api/mtg/admin/save-set-manually/${setCode}`)
    if (response.data.success) {
      showOperationStatus('success', `Extension ${setCode} sauvegardée`)
      await refreshSetStatus(setCode)
    }
  } catch (error) {
    showOperationStatus('error', `Erreur sauvegarde extension ${setCode}`)
  } finally {
    savingSets.value[setCode] = false
  }
}

/**
 * Sauvegarder les cartes en base de données
 */
const saveCardsToDatabase = async (setCode: string) => {
  try {
    savingCards.value[setCode] = true
    const response = await axios.post(`/api/mtg/admin/save-cards-manually/${setCode}`)
    if (response.data.success) {
      showOperationStatus('success', `Cartes ${setCode} sauvegardées`)
      await refreshSetStatus(setCode)
    }
  } catch (error) {
    showOperationStatus('error', `Erreur sauvegarde cartes ${setCode}`)
  } finally {
    savingCards.value[setCode] = false
  }
}

// ========== MÉTHODES D'ACTIONS ==========

/**
 * Télécharger les images d'une extension
 */
const downloadExtensionImages = async (setCode: string) => {
  try {
    downloadingImages.value[setCode] = true
    const response = await axios.post(`/api/images/download-set/${setCode}`)
    if (response.data.success || response.status === 202) {
      showOperationStatus('success', `Téléchargement images démarré pour ${setCode}`)
    }
  } catch (error) {
    showOperationStatus('error', `Erreur téléchargement images ${setCode}`)
  } finally {
    downloadingImages.value[setCode] = false
  }
}

/**
 * Voir les détails d'une extension
 */
const viewSetDetails = (setCode: string) => {
  // Charger les détails et afficher dans l'interface principale
  mtgStore.fetchSetByCode(setCode)
  showOperationStatus('info', `Détails de l'extension ${setCode} chargés`)
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

// ========== COMPUTED PROPERTIES ==========

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

.global-controls {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
}

.api-load-button, .toggle-button {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.9rem;
  transition: all 0.3s ease;
}

.api-load-button {
  background: linear-gradient(45deg, #2196F3, #1976D2);
  color: white;
}

.toggle-button {
  background: linear-gradient(45deg, #3498db, #2980b9);
  color: white;
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
.type-commander { background: #f39c12; color: white; }

.set-date {
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

.manual-controls {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
}

.load-section, .save-section, .action-section {
  display: flex;
  gap: 0.25rem;
}

.load-api-button, .load-scryfall-button,
.save-set-button, .save-cards-button,
.download-button, .view-button {
  flex: 1;
  padding: 0.4rem;
  font-size: 0.75rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
  text-align: center;
  transition: all 0.3s ease;
}

.load-api-button { background: #2196F3; color: white; }
.load-scryfall-button { background: #9C27B0; color: white; }
.save-set-button { background: #4CAF50; color: white; }
.save-cards-button { background: #43A047; color: white; }
.download-button { background: #FF9800; color: white; }
.view-button { background: #607D8B; color: white; }

.load-api-button:hover, .load-scryfall-button:hover,
.save-set-button:hover, .save-cards-button:hover,
.download-button:hover, .view-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
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

  .manual-controls {
    padding: 0.75rem;
  }

  .load-section, .save-section, .action-section {
    flex-direction: column;
  }

  .filters {
    flex-direction: column;
  }

  .filter-select {
    min-width: auto;
  }
}
</style>
