export interface DisciplineResponse {
  id: number;
  name: string;
  active: boolean;
}

export interface DisciplineRequest {
  name: string;
  active?: boolean;
}
