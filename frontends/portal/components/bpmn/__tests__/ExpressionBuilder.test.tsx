import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ExpressionBuilder from '../ExpressionBuilder'

const doaCatalog = {
  doaLevel: [
    { value: 'DOA_L1', label: 'DOA_L1 — Junior Approver' },
    { value: 'DOA_L2', label: 'DOA_L2 — Manager' },
  ]
}

test('shows Input when no catalog for variable', async () => {
  render(<ExpressionBuilder availableVariables={['totalAmount']} catalogValues={doaCatalog} />)
  await userEvent.click(screen.getByText('Add Condition'))
  // Input for value should be visible (no catalog for totalAmount)
  expect(screen.getByPlaceholderText('Value')).toBeInTheDocument()
})

test('shows Select when catalog exists for selected variable', async () => {
  render(<ExpressionBuilder availableVariables={['doaLevel']} catalogValues={doaCatalog} />)
  await userEvent.click(screen.getByText('Add Condition'))
  // Variable defaults to '' — select doaLevel from the variable dropdown first
  const variableTrigger = screen.getAllByRole('combobox')[0]
  await userEvent.click(variableTrigger)
  await userEvent.click(screen.getByRole('option', { name: 'doaLevel' }))
  // Now the value column should show a Select, not a plain Input
  expect(screen.queryByPlaceholderText('Value')).not.toBeInTheDocument()
})
