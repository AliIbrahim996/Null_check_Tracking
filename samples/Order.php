<?php
// A null-heavy PHP example input for Null Check Tracking.

declare(strict_types=1);

final class Order
{
    private ?Customer $customer = null;
    private array $lines = [];
    private ?string $note;

    public function __construct(?Customer $customer, ?string $note = null)
    {
        $this->customer = $customer;
        $this->note = $note;
    }

    public function customerName(): string
    {
        if ($this->customer === null) {
            return 'unknown'; // "if ($x == null)" in a comment or string is ignored
        }
        $name = $this->customer->getName();
        if (is_null($name) || $name === '') {
            return 'anonymous';
        }
        return $name;
    }

    public function shippingCity(?Address $fallback): ?string
    {
        $address = null;
        if ($this->customer !== null) {
            $address = $this->customer->getAddress();
        }
        if ($address == NULL and $fallback != null) {
            $address = $fallback;
        }
        if (!isset($address, $address->city)) {
            return null;
        }
        return $address->city;
    }

    public function firstLine(): ?array
    {
        $first = $this->lines[0] ?? null;
        $label = <<<EOT
            if ($first == null) { this heredoc text is not code }
            EOT;
        if (null === $first) {
            return null;
        }
        $total = array_reduce($this->lines, function ($sum, $line) {
            if ($line['price'] !== null) {
                return $sum + $line['price'];
            }
            return $sum;
        }, 0);
        if ($this->findDiscount($total) != null) {
            $total -= $this->findDiscount($total);
        }
        return $first;
    }

    private function findDiscount(float $total): ?float
    {
        return $total > 100 ? 10.0 : null;
    }
}
?>
<p>Inline HTML is skipped: if ($html == null) { }</p>
