<template>
  <div class="modal-overlay" @click="closeModal">
    <div class="modal-content" @click.stop>
      <button class="close-button" @click="closeModal">×</button>

      <div class="modal-body">
        <div class="card-image-section">
          <img
            v-if="card.imageUrl"
            :src="card.imageUrl"
            :alt="card.name"
            class="modal-card-image"
          />
          <div v-else class="modal-no-image">
            <span>🎴</span>
            <p>Image non disponible</p>
          </div>
        </div>

        <div class="card-details-section">
          <h2 class="modal-card-name">{{ card.name }}</h2>

          <div class="detail-row">
            <strong>Type :</strong>
            <span>{{ card.type }}</span>
          </div>

          <div class="detail-row">
            <strong>Rareté :</strong>
            <span :style="{ color: getRarityColor(card.rarity) }">
              {{ card.rarity }}
            </span>
          </div>

          <div v-if="card.manaCost" class="detail-row">
            <strong>Coût de mana :</strong>
            <span class="mana-cost-display">{{ formatManaCost(card.manaCost) }}</span>
          </div>

          <div v-if="card.cmc !== undefined" class="detail-row">
            <strong>CMC :</strong>
            <span>{{ card.cmc }}</span>
          </div>

          <div v-if="card.colors && card.colors.length > 0" class="detail-row">
            <strong>Couleurs :</strong>
            <span>{{ card.colors.join(', ') }}</span>
          </div>

          <div v-if="card.power && card.toughness" class="detail-row">
            <strong>Force/Endurance :</strong>
            <span class="power-toughness-display">{{ card.power }}/{{ card.toughness }}</span>
          </div>

          <div v-if="card.artist" class="detail-row">
            <strong>Artiste :</strong>
            <span>{{ card.artist }}</span>
          </div>

          <div v-if="card.number" class="detail-row">
            <strong>Numéro :</strong>
            <span>{{ card.number }}</span>
          </div>

          <div v-if="card.text" class="card-text-section">
            <strong>Texte :</strong>
            <p class="card-text">{{ card.text }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MtgCard } from '@/types/mtg'
import { useMtg } from '@/composables/useMtg'

interface Props {
  card: MtgCard
}

defineProps<Props>()

const emit = defineEmits<{
  close: []
}>()

const { formatManaCost, getRarityColor } = useMtg()

const closeModal = () => {
  emit('close')
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}

.modal-content {
  background: white;
  border-radius: 16px;
  max-width: 800px;
  width: 100%;
  max-height: 90vh;
  overflow: auto;
  position: relative;
  color: #333;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
}

.close-button {
  position: absolute;
  top: 1rem;
  right: 1rem;
  background: #e74c3c;
  color: white;
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  font-size: 1.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
  transition: all 0.3s ease;
}

.close-button:hover {
  background: #c0392b;
  transform: scale(1.1);
}

.modal-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  padding: 2rem;
}

.card-image-section {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.modal-card-image {
  max-width: 100%;
  height: auto;
  border-radius: 12px;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
  transition: transform 0.3s ease;
}

.modal-card-image:hover {
  transform: scale(1.02);
}

.modal-no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: #f8f9fa;
  border-radius: 12px;
  color: #666;
  min-height: 300px;
}

.modal-no-image span {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.card-details-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.modal-card-name {
  font-size: 1.8rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 1rem;
  line-height: 1.3;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #ecf0f1;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-row strong {
  min-width: 120px;
  color: #34495e;
  font-weight: 600;
}

.mana-cost-display {
  font-family: 'Courier New', monospace;
  background: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-weight: 600;
  font-size: 1.1rem;
}

.power-toughness-display {
  background: #e74c3c;
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-weight: 600;
}

.card-text-section {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 2px solid #ecf0f1;
}

.card-text-section strong {
  color: #34495e;
  font-weight: 600;
  display: block;
  margin-bottom: 0.5rem;
}

.card-text {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  border-left: 4px solid #3498db;
}

@media (max-width: 768px) {
  .modal-body {
    grid-template-columns: 1fr;
    gap: 1.5rem;
    padding: 1.5rem;
  }

  .modal-card-name {
    font-size: 1.5rem;
  }

  .detail-row {
    flex-direction: column;
    gap: 0.25rem;
  }

  .detail-row strong {
    min-width: auto;
  }
}
</style>
