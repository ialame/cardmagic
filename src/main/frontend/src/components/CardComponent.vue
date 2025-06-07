<template>
  <div
    class="card-component"
    :class="{ 'has-image': card.imageUrl }"
    @click="$emit('click', card)"
  >
    <div class="card-image-container">
      <img
        v-if="card.imageUrl && !imageError"
        :src="card.imageUrl"
        :alt="card.name"
        class="card-image"
        loading="lazy"
        @error="handleImageError"
      />
      <div v-else class="no-image">
        <span>🎴</span>
        <p>Image non disponible</p>
      </div>

      <!-- Overlay avec infos rapides -->
      <div class="card-overlay">
        <div class="quick-info">
          <span
            class="rarity-badge"
            :style="{ backgroundColor: getRarityColor(card.rarity) }"
          >
            {{ card.rarity.charAt(0) }}
          </span>
          <span v-if="card.cmc !== undefined" class="cmc-badge">
            {{ card.cmc }}
          </span>
        </div>
      </div>
    </div>

    <div class="card-info">
      <h3 class="card-name">{{ card.name }}</h3>

      <div class="card-details">
        <span
          class="card-type"
          :style="{ color: getTypeColor(card.type) }"
        >
          {{ card.type }}
        </span>
        <span
          class="card-rarity"
          :style="{ color: getRarityColor(card.rarity) }"
        >
          {{ card.rarity }}
        </span>
      </div>

      <div v-if="card.manaCost" class="mana-cost">
        <span class="mana-label">Coût :</span>
        <span class="mana-symbols">{{ formatManaCost(card.manaCost) }}</span>
      </div>

      <div v-if="card.power && card.toughness" class="power-toughness">
        {{ card.power }}/{{ card.toughness }}
      </div>

      <div v-if="card.artist" class="artist-info">
        <span class="artist-label">🎨</span>
        <span class="artist-name">{{ card.artist }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { MtgCard } from '@/types/mtg'
import { useMtg } from '@/composables/useMtg'

interface Props {
  card: MtgCard
}

defineProps<Props>()
defineEmits<{
  click: [card: MtgCard]
}>()

const { formatManaCost, getRarityColor, getTypeColor } = useMtg()
const imageError = ref(false)

const handleImageError = () => {
  imageError.value = true
}
</script>

<style scoped>
.card-component {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;
  cursor: pointer;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  color: #333;
  position: relative;
}

.card-component:hover {
  transform: translateY(-8px);
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.2);
}

.card-component:hover .card-overlay {
  opacity: 1;
}

.card-image-container {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
  background: linear-gradient(45deg, #f0f0f0, #e0e0e0);
}

.card-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.card-component:hover .card-image {
  transform: scale(1.05);
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(0, 0, 0, 0.7), transparent);
  opacity: 0;
  transition: opacity 0.3s ease;
  padding: 0.5rem;
}

.quick-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.rarity-badge {
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-weight: 700;
  font-size: 0.8rem;
  text-transform: uppercase;
  min-width: 24px;
  text-align: center;
}

.cmc-badge {
  background: rgba(255, 255, 255, 0.9);
  color: #333;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-weight: 700;
  font-size: 0.8rem;
  min-width: 24px;
  text-align: center;
}

.no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  background: linear-gradient(45deg, #f8f9fa, #e9ecef);
}

.no-image span {
  font-size: 3rem;
  margin-bottom: 0.5rem;
  opacity: 0.7;
}

.no-image p {
  font-size: 0.9rem;
  margin: 0;
  opacity: 0.8;
}

.card-info {
  padding: 1rem;
}

.card-name {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #2c3e50;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  gap: 0.5rem;
}

.card-type {
  font-size: 0.85rem;
  font-weight: 500;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-rarity {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.mana-cost {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.mana-label {
  font-size: 0.8rem;
  color: #7f8c8d;
  font-weight: 500;
}

.mana-symbols {
  font-family: 'Segoe UI Emoji', 'Apple Color Emoji', sans-serif;
  font-weight: 600;
  background: #f8f9fa;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.9rem;
  border: 1px solid #dee2e6;
}

.power-toughness {
  background: linear-gradient(45deg, #e74c3c, #c0392b);
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-weight: 600;
  font-size: 0.9rem;
  width: fit-content;
  margin-left: auto;
  box-shadow: 0 2px 4px rgba(231, 76, 60, 0.3);
}

.artist-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid #dee2e6;
}

.artist-label {
  font-size: 0.8rem;
}

.artist-name {
  font-size: 0.8rem;
  color: #6c757d;
  font-style: italic;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Animations d'entrée */
.card-component {
  animation: cardAppear 0.6s ease-out;
}

@keyframes cardAppear {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 480px) {
  .card-image-container {
    height: 150px;
  }

  .card-info {
    padding: 0.75rem;
  }

  .card-name {
    font-size: 1rem;
  }

  .card-details {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .mana-cost {
    flex-wrap: wrap;
  }
}
</style>
