import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ExchangeRateResponse } from '../models/exchange-rate.model';

/** `GET /api/exchange` — contracts/exchange.md. */
@Injectable({ providedIn: 'root' })
export class ExchangeRateService {
  private readonly http = inject(HttpClient);

  getRate(from: string, to: string, date?: string): Observable<ExchangeRateResponse> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<ExchangeRateResponse>(`${environment.apiBaseUrl}/exchange`, { params });
  }
}
