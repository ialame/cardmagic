// src/composables/useMtg.ts
import { ref, computed } from 'vue'
import type { MtgCard } from '@/types/mtg'

export function useMtg() {
  const selectedCard = ref<MtgCard | null>(null)
  const searchTerm = ref('')

  const filterCardsBySearch = (cards: MtgCard[], search: string): MtgCard[] => {
    if (!search.trim()) return cards

    const term = search.toLowerCase().trim()
    return cards.filter(card =>
      card.name.toLowerCase().includes(term) ||
      card.type.toLowerCase().includes(term) ||
      card.rarity.toLowerCase().includes(term) ||
      (card.text && card.text.toLowerCase().includes(term)) ||
      (card.artist && card.artist.toLowerCase().includes(term))
    )
  }

  const getCardsByRarity = (cards: MtgCard[]) => {
    return cards.reduce((acc, card) => {
      const rarity = card.rarity || 'Unknown'
      if (!acc[rarity]) acc[rarity] = []
      acc[rarity].push(card)
      return acc
    }, {} as Record<string, MtgCard[]>)
  }

  const formatManaCost = (manaCost?: string): string => {
    if (!manaCost) return ''
    // Remplace les symboles de mana par une version plus lisible
    return manaCost
      .replace(/\{([^}]+)\}/g, '$1')
      .replace(/W/g, '⚪')
      .replace(/U/g, '🔵')
      .replace(/B/g, '⚫')
      .replace(/R/g, '🔴')
      .replace(/G/g, '🟢')
      .replace(/C/g, '◇')
  }

  const getRarityColor = (rarity: string): string => {
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

  const getTypeColor = (type: string): string => {
    if (type.includes('Creature')) return '#2ecc71'
    if (type.includes('Sorcery') || type.includes('Instant')) return '#e74c3c'
    if (type.includes('Enchantment')) return '#9b59b6'
    if (type.includes('Artifact')) return '#95a5a6'
    if (type.includes('Land')) return '#8b4513'
    if (type.includes('Planeswalker')) return '#f39c12'
    return '#34495e'
  }

  return {
    selectedCard,
    searchTerm,
    filterCardsBySearch,
    getCardsByRarity,
    formatManaCost,
    getRarityColor,
    getTypeColor
  }
}
