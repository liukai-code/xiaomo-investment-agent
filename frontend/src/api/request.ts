import axios from 'axios'
import router from '@/router'

const request = axios.create({
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('email')
      localStorage.removeItem('accountId')
      router.push('/login')
    }
    return Promise.reject(err)
  },
)

export default request
