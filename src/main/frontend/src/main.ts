import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

console.log('🚀 Application MTG Cards en cours de démarrage...')

try {
  const app = createApp(App)

  // Ajout de Pinia pour la gestion d'état
  app.use(createPinia())

  // Montage de l'application
  app.mount('#app')

  console.log('✅ Application MTG Cards montée avec succès !')
  console.log('🎴 Prêt à explorer les cartes Magic: The Gathering !')
} catch (error) {
  console.error('❌ Erreur lors du montage de l\'application:', error)
}
