terraform {
  required_version = ">= 1.6"

  required_providers {
    datadog = {
      source  = "DataDog/datadog"
      version = "~> 3.0"
    }
  }
}

# 鍵は環境変数 DD_API_KEY と DD_APP_KEY で渡す（ファイルに書かない）
provider "datadog" {
  api_url = "https://api.${var.datadog_site}/"
}
