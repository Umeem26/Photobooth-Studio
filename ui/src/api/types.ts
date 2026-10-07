export interface BoothConfig {
  'event.name': string;
  'event.date': string;
  maxRetakes: number;
  photosPerLayout: Record<string, number>;
}

export interface Layout {
  id: string;
  name: string;
  description: string;
  photos: number;
  orientation: 'vertical' | 'horizontal';
}

export interface FilterOption {
  id: string;
  name: string;
}
