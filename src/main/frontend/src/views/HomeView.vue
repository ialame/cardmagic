<template>
  <div class="home">
    <!-- Nouveau sélecteur d'extensions -->
    <SetSelector />
    <!-- Contrôles -->
    <div class="controls">
      <!-- Bouton pour la dernière extension -->
      <button
        @click="loadLatestSet"
        :disabled="loading"
        class="load-button"
      >
        {{ loading ? 'Chargement...' : '⭐ Dernière extension' }}
      </button>

      <!-- Extensions populaires rapides -->
      <div class="quick-sets">
        <button
          v-for="quickSet in quickSets"
          :key="quickSet.code"
          @click="loadSpecificSet(quickSet.code)"
          :disabled="loading"
          class="quick-set-button"
          :title="quickSet.name"
        >
          {{ quickSet.code }}
        </button>
      </div>

      <div v-if="hasLatestSet" class="search-container">
        <input
          v-model="searchTerm"
          placeholder="Rechercher une carte..."
          class="search-input"
        />
      </div>
    </div>

    <!-- Affichage des erreurs -->
    <div v-if="error" class="error-message">
      {{ error }}
      <button @click="clearError" class="close-error">×</button>
    </div>

    <!-- Informations sur l'extension -->
    <div v-if="latestSet" class="set-info fade-in">
      <h2>{{ latestSet.name }} ({{ latestSet.code }})</h2>
      <div class="set-details">
        <span class="badge">{{ latestSet.type }}</span>
        <span v-if="latestSet.releaseDate" class="release-date">
          Sortie : {{ formatDate(latestSet.releaseDate) }}
        </span>
        <span class="card-count">
          {{ filteredCards.length }} carte(s)
          <span v-if="searchTerm">({{ latestSetCards.length }} au total)</span>
        </span>
      </div>
    </div>

    <!-- Statistiques par rareté -->
    <div v-if="hasLatestSet && !searchTerm" class="rarity-stats fade-in">
      <h3>📊 Répartition par rareté</h3>
      <div class="rarity-grid">
        <div
          v-for="(cards, rarity) in cardsByRarity"
          :key="rarity"
          class="rarity-item"
          :style="{ borderColor: getRarityColor(rarity) }"
        >
          <span class="rarity-name" :style="{ color: getRarityColor(rarity) }">{{ rarity }}</span>
          <span class="rarity-count">{{ cards.length }}</span>
        </div>
      </div>
    </div>

    <!-- Grille des cartes -->
    <div v-if="filteredCards.length > 0" class="cards-grid fade-in">
      <CardComponent
        v-for="card in filteredCards"
        :key="card.id"
        :card="card"
        @click="selectCard(card)"
      />
    </div>

    <!-- Message si aucune carte -->
    <div v-else-if="hasLatestSet && !loading" class="no-cards">
      <p v-if="searchTerm">Aucune carte trouvée pour "{{ searchTerm }}"</p>
      <p v-else>Aucune carte disponible pour cette extension</p>
    </div>

    <!-- Message d'accueil -->
    <div v-else-if="!loading" class="welcome-message">
      <div class="welcome-content">
        <h2>🎴 Bienvenue dans l'explorateur de cartes Magic !</h2>
        <p>Cliquez sur le bouton ci-dessus pour découvrir les cartes de la dernière extension.</p>
        <div class="features">
          <div class="feature">
            <span class="feature-icon">🔍</span>
            <span>Recherche avancée</span>
          </div>
          <div class="feature">
            <span class="feature-icon">🎨</span>
            <span>Images haute qualité</span>
          </div>
          <div class="feature">
            <span class="feature-icon">📊</span>
            <span>Statistiques détaillées</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal détail carte -->
    <CardModal
      v-if="selectedCard"
      :card="selectedCard"
      @close="selectedCard = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useMtgStore } from '@/stores/mtgStore'
import { useMtg } from '@/composables/useMtg'
import CardComponent from '@/components/CardComponent.vue'
import CardModal from '@/components/CardModal.vue'
import SetSelector from '@/components/SetSelector.vue' // Nouveau import

// Extensions populaires pour accès rapide
const quickSets = [
  { code: 'BLB', name: 'Bloomburrow' },
  { code: 'MH3', name: 'Modern Horizons 3' },
  { code: 'OTJ', name: 'Outlaws of Thunder Junction' },
  { code: 'MKM', name: 'Murders at Karlov Manor' },
  { code: 'LCI', name: 'The Lost Caverns of Ixalan' }
]

const mtgStore = useMtgStore()
const { selectedCard, searchTerm, filterCardsBySearch, getRarityColor } = useMtg()

// Computed properties
const loading = computed(() => mtgStore.loading)
const error = computed(() => mtgStore.error)
const latestSet = computed(() => mtgStore.latestSet)
const hasLatestSet = computed(() => mtgStore.hasLatestSet)
const latestSetCards = computed(() => mtgStore.latestSetCards)
const cardsByRarity = computed(() => mtgStore.cardsByRarity)

const filteredCards = computed(() =>
  filterCardsBySearch(latestSetCards.value, searchTerm.value)
)

// Nouvelle méthode pour charger une extension spécifique
const loadSpecificSet = (setCode: string) => {
  console.log('🎯 Chargement de l\'extension:', setCode)
  mtgStore.fetchSetByCode(setCode)
}

// Methods
const loadLatestSet = () => {
  console.log('🎴 Chargement de la dernière extension...')
  mtgStore.fetchLatestSetWithCards().then(() => {
    console.log('📊 Extension chargée:', mtgStore.latestSet)
    console.log('🎯 Nombre de cartes:', mtgStore.latestSetCards?.length)
    console.log('🎨 Première carte:', mtgStore.latestSetCards?.[0])
  })
}

const clearError = () => {
  mtgStore.clearError()
}

const selectCard = (card: any) => {
  selectedCard.value = card
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
  console.log('🏠 HomeView monté - Chargement automatique de la dernière extension')
  loadLatestSet()
})
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
}

.controls {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  flex-wrap: wrap;
  align-items: center;
}

.load-button {
  padding: 0.75rem 1.5rem;
  background: linear-gradient(45deg, #ff6b6b, #ee5a24);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 200px;
}

.load-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(238, 90, 36, 0.3);
}

.load-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.search-container {
  flex: 1;
  max-width: 300px;
}

.search-input {
  width: 100%;
  padding: 0.75rem;
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.7);
}

.search-input:focus {
  outline: none;
  border-color: #ffd700;
  background: rgba(255, 255, 255, 0.15);
  box-shadow: 0 0 10px rgba(255, 215, 0, 0.3);
}

.error-message {
  background: #e74c3c;
  color: white;
  padding: 1rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  animation: shake 0.5s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}

.close-error {
  background: none;
  border: none;
  color: white;
  font-size: 1.5rem;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background 0.3s ease;
}

.close-error:hover {
  background: rgba(255, 255, 255, 0.2);
}

.set-info {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  padding: 1.5rem;
  border-radius: 12px;
  margin-bottom: 2rem;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.set-info h2 {
  margin-bottom: 1rem;
  font-size: 1.8rem;
  color: #ffd700;
  text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.5);
}

.set-details {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: center;
}

.badge {
  background: #3498db;
  color: white;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.release-date,
.card-count {
  background: rgba(255, 255, 255, 0.2);
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.9rem;
  backdrop-filter: blur(5px);
}

.rarity-stats {
  margin-bottom: 2rem;
}

.rarity-stats h3 {
  margin-bottom: 1rem;
  color: #ffd700;
}

.rarity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1rem;
}

.rarity-item {
  background: rgba(255, 255, 255, 0.1);
  padding: 1rem;
  border-radius: 8px;
  border-left: 4px solid;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: transform 0.3s ease;
}

.rarity-item:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.15);
}

.rarity-name {
  font-weight: 600;
  font-size: 0.9rem;
}

.rarity-count {
  font-size: 1.2rem;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.2);
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  min-width: 30px;
  text-align: center;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1.5rem;
  margin-top: 2rem;
}

.no-cards {
  text-align: center;
  padding: 3rem;
  font-size: 1.2rem;
  opacity: 0.8;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  margin: 2rem 0;
}

.welcome-message {
  text-align: center;
  padding: 3rem;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  backdrop-filter: blur(10px);
  margin: 2rem 0;
}

.welcome-content h2 {
  color: #ffd700;
  margin-bottom: 1rem;
  font-size: 2rem;
}

.welcome-content p {
  font-size: 1.2rem;
  margin-bottom: 2rem;
  opacity: 0.9;
}

.features {
  display: flex;
  justify-content: center;
  gap: 2rem;
  flex-wrap: wrap;
}

.feature {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  min-width: 120px;
}

.feature-icon {
  font-size: 2rem;
}

/* Animations d'entrée */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in {
  animation: fadeIn 0.6s ease-out;
}

@media (max-width: 768px) {
  .controls {
    flex-direction: column;
    align-items: stretch;
  }

  .search-container {
    max-width: none;
  }

  .cards-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 1rem;
  }

  .set-details {
    flex-direction: column;
    align-items: flex-start;
  }

  .rarity-grid {
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  }

  .features {
    flex-direction: column;
    align-items: center;
  }
}

.quick-sets {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.quick-set-button {
  padding: 0.5rem 0.75rem;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  font-family: 'Courier New', monospace;
  transition: all 0.3s ease;
  font-size: 0.9rem;
}

.quick-set-button:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  border-color: #ffd700;
  transform: translateY(-1px);
}

.load-button.latest {
  background: linear-gradient(45deg, #ffd700, #ffab00);
  color: #2c3e50;
}

</style>
