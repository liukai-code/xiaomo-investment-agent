import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { vAnimate } from './composables/useScrollAnimation'

import 'katex/dist/katex.min.css'
import './styles/variables.css'
import './styles/login.css'
import './styles/markdown.css'
import './styles/portal.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('animate', vAnimate)
app.mount('#app')
