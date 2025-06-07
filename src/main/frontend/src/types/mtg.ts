// src/types/mtg.ts
export interface MtgCard {
  id: string
  name: string
  manaCost?: string
  cmc?: number
  colors?: string[]
  colorIdentity?: string[]
  type: string
  supertypes?: string[]
  types?: string[]
  subtypes?: string[]
  rarity: string
  set: string
  setName?: string
  text?: string
  artist?: string
  number?: string
  power?: string
  toughness?: string
  layout?: string
  multiverseid?: number
  imageUrl?: string
}

export interface MtgSet {
  code: string
  name: string
  type: string
  block?: string
  releaseDate?: string
  gathererCode?: string
  magicCardsInfoCode?: string
  border?: string
  onlineOnly: boolean
  cards?: MtgCard[]
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  timestamp: string
}

export interface MtgApiError {
  message: string
  status?: number
}
