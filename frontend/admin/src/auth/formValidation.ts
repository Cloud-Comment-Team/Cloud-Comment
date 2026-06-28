export const EMAIL_MAX_LENGTH = 320
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 72

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(value: string): true | string {
  const email = value.trim()

  if (!email) {
    return 'Введите email'
  }

  if (email.length > EMAIL_MAX_LENGTH) {
    return `Email должен быть не длиннее ${EMAIL_MAX_LENGTH} символов`
  }

  if (!EMAIL_PATTERN.test(email)) {
    return 'Введите корректный email'
  }

  return true
}

export function validatePassword(value: string): true | string {
  if (!value.trim()) {
    return 'Введите пароль'
  }

  if (value.length < PASSWORD_MIN_LENGTH) {
    return `Пароль должен быть не короче ${PASSWORD_MIN_LENGTH} символов`
  }

  if (value.length > PASSWORD_MAX_LENGTH) {
    return `Пароль должен быть не длиннее ${PASSWORD_MAX_LENGTH} символов`
  }

  return true
}

export function validatePasswordConfirmation(value: string, password: string): true | string {
  if (!value) {
    return 'Повторите пароль'
  }

  if (value !== password) {
    return 'Пароли не совпадают'
  }

  return true
}

export function getFieldFeedback(
  isDirty: boolean | undefined,
  value: string,
  error: string | undefined,
  hint: string,
  success: string,
) {
  if (isDirty && error) {
    return { error }
  }

  if (isDirty && value) {
    return { success }
  }

  return { hint }
}
