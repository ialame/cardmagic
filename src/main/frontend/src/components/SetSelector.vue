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
          :class="{ 'selected': selectedSet?.code === set.code }"
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
          </div>

          <div class="set-actions">
            <button
              @click.stop="loadSet(set.code)"
              :disabled="loading"
              class="load-button"
            >
              {{ loading && selectedSet?.code === set.code ? 'Chargement...' : 'Charger' }}
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMtgStore } from '@/stores/mtgStore'
import type { MtgSet } from '@/types/mtg'

const mtgStore = useMtgStore()

// État local
const showSelector = ref(false)
const searchTerm = ref('')
const selectedType = ref('')
const sortBy = ref('releaseDate')
const selectedSet = ref<MtgSet | null>(null)
const currentPage = ref(1)
const itemsPerPage = 12

// Computed
const loading = computed(() => mtgStore.loading)
const allSets = computed(() => mtgStore.sets)

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
    await mtgStore.fetchAllSetsOnly()
  }
}

const selectSet = (set: MtgSet) => {
  selectedSet.value = set
}

const loadSet = async (setCode: string) => {
  selectedSet.value = allSets.value.find(s => s.code === setCode) || null
  await mtgStore.fetchSetByCode(setCode)
  showSelector.value = false
}

const formatDate = (dateString: string): string => {
  try {
    return new Date(dateString).toLocaleDateString('fr-FR')
  } catch {
    return dateString
  }
}

// Lifecycle
onMounted(() => {
  console.log('🎛️ SetSelector monté')
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
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
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
  justify-content: space-between;
  align-items: center;
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

.set-info {
  flex: 1;
}

.set-name {
  margin: 0 0 0.5rem 0;
  color: #ffd700;
  font-size: 1.1rem;
}

.set-details {
  display: flex;
  gap: 0.5rem;
  margin: 0.25rem 0;
  align-items: center;
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

.set-actions {
  margin-left: 1rem;
}

.load-button {
  padding: 0.5rem 1rem;
  background: linear-gradient(45deg, #ff6b6b, #ee5a24);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
  min-width: 80px;
}

.load-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(238, 90, 36, 0.3);
}

.load-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

@media (max-width: 768px) {
  .sets-grid {
    grid-template-columns: 1fr;
  }

  .set-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .set-actions {
    margin-left: 0;
    align-self: stretch;
  }

  .load-button {
    width: 100%;
  }

  .filters {
    flex-direction: column;
  }

  .filter-select {
    min-width: auto;
  }
}
</style>
