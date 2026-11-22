import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import localeId from '@angular/common/locales/id';
import { LOCALE_ID } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';

registerLocaleData(localeId);

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(),
    { provide: LOCALE_ID, useValue: 'id-ID' },
  ],
}).catch(console.error);
