// A null-heavy C++ example input for Null Check Tracking.
#include <cstddef>
#include <memory>
#include <string>

struct Item {
    std::string name;
    Item* next = nullptr;
};

class Inventory {
    Item* head_ = nullptr;
    std::shared_ptr<Item> featured_;

public:
    Item* find(const std::string& name, Item* start) {
        Item* item = start;
        if (item == nullptr) {
            item = head_;
        }
        while (item != nullptr && item->name != name) { // a while loop is not an if statement
            item = item->next;
        }
        if (!item || NULL == item->next) {
            return item; // "if (item == nullptr)" in a comment or string is ignored
        }
        return item;
    }

    void add(Item* item) {
        if (item == NULL) {
            return;
        }
        if (this->head_) {
            item->next = head_;
        }
        head_ = item;
    }

    bool hasFeatured() const;
    void clear();
};

bool Inventory::hasFeatured() const {
    if (featured_ != nullptr && featured_->next != nullptr) {
        return true;
    }
    return false;
}

void Inventory::clear() {
    Item* current = head_;
    if (Item* first = current) {
        head_ = nullptr;
    }
    auto remove = [](Item* gone) {
        if (gone != nullptr) {
            delete gone;
        }
    };
    if (current != nullptr) {
        remove(current);
    }
}
