import { describe, expect, it } from "vitest"
import { messageSansReponse, numerosSansReponse } from "@/lib/passation"

const questions = [{ id: 11 }, { id: 12 }, { id: 13 }, { id: 14 }]

describe("questions sans réponse (confirmation de soumission)", () => {
  it("donne les numéros (1, 2…) dans l'ordre de l'épreuve, pas les identifiants", () => {
    expect(numerosSansReponse(questions, (id) => id === 12)).toEqual([1, 3, 4])
    expect(numerosSansReponse(questions, () => true)).toEqual([])
  })

  it("rédige le message au singulier et au pluriel", () => {
    expect(messageSansReponse([])).toBeNull()
    expect(messageSansReponse([3])).toBe("La question 3 est sans réponse et sera comptée comme non traitée.")
    expect(messageSansReponse([1, 3, 4])).toBe(
      "Les questions 1, 3 et 4 sont sans réponse et seront comptées comme non traitées."
    )
  })
})
