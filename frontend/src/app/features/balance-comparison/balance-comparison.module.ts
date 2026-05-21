/**
 * balance-comparison.module.ts
 * Lazy-loaded feature module for balance-comparison.
 * Source: task-plan §Step 5.
 */

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BalanceComparisonRoutingModule } from './balance-comparison-routing.module';

@NgModule({
  imports: [CommonModule, BalanceComparisonRoutingModule],
})
export class BalanceComparisonModule {}
